package org.reso.service.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.*;
import org.reso.service.data.GenericEntityCollectionProcessor;
import org.reso.service.data.GenericEntityProcessor;
import org.reso.service.data.definition.LookupDefinition;
import org.reso.service.data.meta.builder.DefinitionBuilder;
import org.reso.service.data.definition.FieldDefinition;
import org.reso.service.data.meta.ResourceInfo;
import org.reso.service.edmprovider.RESOedmProvider;
import org.reso.service.security.Validator;
import org.reso.service.security.providers.BearerAuthProvider;
import org.reso.service.servlet.util.ClassLoader;
import org.reso.service.servlet.util.SimpleError;
import org.reso.service.tenant.ODataHandlerCache;
import org.reso.service.tenant.TenantConfig;
import org.reso.service.tenant.TenantConfigurationService;
import org.reso.service.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;

public class RESOservlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(RESOservlet.class);
    private static MongoClient mongoClient = null;
    private Validator validator = null;
    private OData odata = null;
    
    // Resource lookup is now tenant-specific
    private Map<String, Map<String, ResourceInfo>> tenantResourceLookup = new HashMap<>();

    public static MongoClient getMongoClient() {
        return mongoClient;
    }

    @Override
    public void init() throws ServletException {
        super.init();

        LOG.info("Initializing RESOservlet with multi-tenant support");
        
        Map<String, String> env = System.getenv();
        for (String envName : env.keySet()) {
            LOG.debug(String.format("ENV VAR: %s=%s%n",
                    envName,
                    env.get(envName)));
        }

        this.validator = new Validator();
        this.validator.addProvider(new BearerAuthProvider());

        String mongoConnStr = env.getOrDefault("SPRING_DATA_MONGODB_URI", "mongodb://mongo-db:27017/reso");
        LOG.info("Connecting to MongoDB with URI: {}", mongoConnStr.replaceAll(":[^/]+@", ":****@"));

        try {
            // Initialize MongoDB client
            mongoClient = MongoClients.create(mongoConnStr);
            LOG.info("Connected to MongoDB!");
            
            // Initialize the tenant configuration service
            TenantConfigurationService.initialize(mongoClient);
            
        } catch (Exception e) {
            LOG.error("Server Error occurred in connecting to MongoDB", e);
            throw new ServletException("Failed to connect to MongoDB", e);
        }

        // Set up ODATA
        this.odata = OData.newInstance();
        
        // Initialize default tenant
        initializeTenant("default");
    }
    
    /**
     * Initialize resources for a specific tenant
     * 
     * @param tenantId The tenant ID
     * @throws ServletException If initialization fails
     */
    private void initializeTenant(String tenantId) throws ServletException {
        LOG.info("Initializing tenant: {}", tenantId);
        
        // Set the current tenant context for this thread during initialization
        TenantContext.setCurrentTenant(tenantId);
        
        try {
            // Get tenant configuration
            TenantConfig config = TenantConfigurationService.getTenantConfig(tenantId);
            if (config == null) {
                LOG.error("No configuration found for tenant: {}", tenantId);
                throw new ServletException("No configuration found for tenant: " + tenantId);
            }
            
            LOG.info("Using metadata file: {} and lookup type: {} for tenant: {}", 
                    config.getMetadataFilePath(), config.getLookupType(), tenantId);
            
            // Create EDM provider
            RESOedmProvider edmProvider = new RESOedmProvider();
            
            // Initialize resource lookup for this tenant if it doesn't exist
            tenantResourceLookup.putIfAbsent(tenantId, new HashMap<>());
            Map<String, ResourceInfo> resourceLookup = tenantResourceLookup.get(tenantId);
            
            ArrayList<ResourceInfo> resources = new ArrayList<>();
            
            // We are going to use a custom field definition to query Fields
            FieldDefinition fieldDefinition = new FieldDefinition();
            resources.add(fieldDefinition);
            fieldDefinition.addResources(resources);
            resourceLookup.put(fieldDefinition.getResourceName(), fieldDefinition);
            
            // If there is a Certification metadata report file, import it for class definitions
            String definitionFile = config.getMetadataFilePath();
            if (definitionFile != null) {
                // Create tenant-specific definition builder with tenant's lookup type
                DefinitionBuilder definitionBuilder = new DefinitionBuilder(definitionFile);
                List<ResourceInfo> loadedResources = definitionBuilder.readResources();
                
                for (ResourceInfo resource : loadedResources) {
                    if (!(resource.getResourceName()).equals("Field") && !(resource.getResourceName()).equals("Lookup")) {
                        try {
                            resource.findMongoPrimaryKey(mongoClient);
                            resources.add(resource);
                            resourceLookup.put(resource.getResourceName(), resource);
                        } catch (Exception e) {
                            LOG.error("Error with: " + resource.getResourceName() + " - " + e.getMessage());
                        }
                    }
                }
            } else {
                // Get all classes with constructors with 0 parameters
                try {
                    Class[] classList = ClassLoader.getClasses("org.reso.service.data.definition.custom");
                    for (Class classProto : classList) {
                        Constructor ctor = null;
                        Constructor[] ctors = classProto.getDeclaredConstructors();
                        for (int i = 0; i < ctors.length; i++) {
                            ctor = ctors[i];
                            if (ctor.getGenericParameterTypes().length == 0)
                                break;
                        }
                        if (ctor != null) {
                            ctor.setAccessible(true);
                            ResourceInfo resource = (ResourceInfo) ctor.newInstance();
                            
                            try {
                                resource.findMongoPrimaryKey(mongoClient);
                                resources.add(resource);
                                resourceLookup.put(resource.getResourceName(), resource);
                            } catch (Exception e) {
                                LOG.error("Error with: " + resource.getResourceName() + " - " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.error(e.getMessage());
                }
            }
            
            LookupDefinition defn = new LookupDefinition();
            try {
                defn.findMongoPrimaryKey(mongoClient);
                resources.add(defn);
                resourceLookup.put(defn.getResourceName(), defn);
                LookupDefinition.loadCache(mongoClient, defn);
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
            
            ServiceMetadata edm = odata.createServiceMetadata(edmProvider, new ArrayList<EdmxReference>());
            
            // Create handler and cache it
            ODataHttpHandler handler = ODataHandlerCache.cacheHandler(tenantId, odata, edm);
            
            // Register processors
            GenericEntityCollectionProcessor entityCollectionProcessor = new GenericEntityCollectionProcessor(mongoClient);
            GenericEntityProcessor entityProcessor = new GenericEntityProcessor(mongoClient);
            
            handler.register(entityCollectionProcessor);
            handler.register(entityProcessor);
            
            for (ResourceInfo resource : resources) {
                LOG.info("Resource importing for tenant {}: {}", tenantId, resource.getResourceName());
                edmProvider.addDefinition(resource);
                
                entityCollectionProcessor.addResource(resource, resource.getResourceName());
                entityProcessor.addResource(resource, resource.getResourceName());
            }
            
            // We want to pre-load ALL the metadata. The best way is to do a $metadata request.
            ODataRequest request = new ODataRequest();
            request.setRawODataPath("/$metadata");
            request.setMethod(HttpMethod.GET);
            request.setProtocol("HTTP/1.1");
            handler.process(request);
            
            LOG.info("Tenant {} initialized successfully with {} resources", tenantId, resources.size());
            
        } catch (Exception e) {
            LOG.error("Error initializing tenant: " + tenantId, e);
            throw new ServletException("Failed to initialize tenant: " + tenantId, e);
        } finally {
            // Clear the tenant context
            TenantContext.clear();
        }
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        
        if (!this.validator.verify(req) && this.validator.unauthorizedResponse(resp)) {
            SimpleError error = new SimpleError(SimpleError.AUTH_REQUIRED);
            ObjectMapper objectMapper = new ObjectMapper();

            PrintWriter out = resp.getWriter();
            out.println(objectMapper.writeValueAsString(error));
            out.flush();
            return;
        }
        
        // Resolve the tenant for this request
        String tenantId = resolveTenantId(req);
        TenantContext.setCurrentTenant(tenantId);
        
        try {
            // Get or create handler for this tenant
            ODataHttpHandler handler = getOrCreateHandler(tenantId);
            
            // Process the request with the tenant-specific handler
            handler.process(req, resp);
            
        } catch (RuntimeException e) {
            LOG.error("Server Error occurred in RESOservlet for tenant: " + tenantId, e);
            throw new ServletException(e);
        } finally {
            // Clear the tenant context
            TenantContext.clear();
        }
    }
    
    /**
     * Resolve the tenant ID from the request
     * This is a simplified version - for production, use the TenantFilter instead
     * 
     * @param request The HTTP request
     * @return The tenant ID
     */
    private String resolveTenantId(HttpServletRequest request) {
        // Check for tenant ID in header
        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }
        
        // Check subdomain
        String host = request.getServerName();
        if (host.contains(".")) {
            tenantId = host.substring(0, host.indexOf('.'));
            return tenantId;
        }
        
        // Default to "default" tenant
        return "default";
    }
    
    /**
     * Get or create a handler for the specified tenant
     * 
     * @param tenantId The tenant ID
     * @return The OData HTTP handler
     * @throws ServletException If handler creation fails
     */
    private ODataHttpHandler getOrCreateHandler(String tenantId) throws ServletException {
        // Check cache first
        ODataHttpHandler handler = ODataHandlerCache.getHandler(tenantId);
        
        // If not in cache, initialize the tenant
        if (handler == null) {
            LOG.info("No handler found for tenant {}, initializing...", tenantId);
            initializeTenant(tenantId);
            handler = ODataHandlerCache.getHandler(tenantId);
            
            if (handler == null) {
                LOG.error("Failed to create handler for tenant: {}", tenantId);
                throw new ServletException("Failed to create handler for tenant: " + tenantId);
            }
        }
        
        return handler;
    }
    
    /**
     * Get the resource lookup for a specific tenant
     * 
     * @param tenantId The tenant ID
     * @return The resource lookup map
     */
    public Map<String, ResourceInfo> getResourceLookupForTenant(String tenantId) {
        return tenantResourceLookup.getOrDefault(tenantId, new HashMap<>());
    }
}
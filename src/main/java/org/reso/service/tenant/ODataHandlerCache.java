package org.reso.service.tenant;

import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;
import org.bson.Document;
import org.reso.service.data.GenericEntityCollectionProcessor;
import org.reso.service.data.GenericEntityProcessor;
import org.reso.service.data.definition.FieldDefinition;
import org.reso.service.data.definition.LookupDefinition;
import org.reso.service.data.meta.ResourceInfo;
import org.reso.service.data.meta.builder.DefinitionBuilder;
import org.reso.service.data.mongodb.MongoDBManager;
import org.reso.service.edmprovider.RESOedmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import org.reso.service.servlet.util.ClassLoader;

public class ODataHandlerCache {
  private static final Logger LOG = LoggerFactory.getLogger(ODataHandlerCache.class);
  // Maximum number of handlers entries in the cache
  private static final int MAX_CACHE_SIZE = 100;
  private static Map<String, Map<String, ResourceInfo>> serverResourceLookup = new HashMap<>();

  private static final Map<String, ODataHttpHandler> handlers = Collections.synchronizedMap(
      new LinkedHashMap<String, ODataHttpHandler>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ODataHttpHandler> eldest) {
          return size() > MAX_CACHE_SIZE;
        }
      });

  public static ODataHttpHandler getHandler(String clientId) throws ServletException {
    // First, check our cache.
    ServerConfig serverConfig = ServersConfigurationService.getServerConfig(clientId);
    ODataHttpHandler handler = handlers.get(serverConfig.getUniqueServerConfigName());
    if (handler != null) {
      return handler;
    }

    // Not in cache so query MongoDB 
    MongoDatabase database = MongoDBManager.getDatabase();
    MongoCollection<Document> collection = database.getCollection("certification_reports");
    Document query = new Document("certificationReportId", serverConfig.getCertificationReportId());
    Document doc = collection.find(query).first();

    if (doc == null) {
      return null;
    }

    CertificationReport certificationReport = new CertificationReport();
    certificationReport.setCertificationReportId(serverConfig.getCertificationReportId());
    certificationReport.setProviderUoi(doc.getString("providerUoi"));
    certificationReport.setRecipientUoi(doc.getString("recipientUoi"));
    
    Object report = doc.get("report");
    Document reportDoc = (org.bson.Document) report;
    certificationReport.setReport(reportDoc.toJson());

    handler = createHandler(clientId, certificationReport.getReport(), serverConfig);
    handlers.put(serverConfig.getUniqueServerConfigName(), handler);
    return handler;

  }

  private static ODataHttpHandler createHandler(String clientId, String metadata, ServerConfig serverConfig) throws ServletException {
    LOG.info("creating handler for certification report: {}", serverConfig.getUniqueServerConfigName());

    try {

      // Create EDM provider
      MongoClient mongoClient = MongoDBManager.getClient();
      RESOedmProvider edmProvider = new RESOedmProvider();

      serverResourceLookup.putIfAbsent(serverConfig.getUniqueServerConfigName(), new HashMap<>());
      Map<String, ResourceInfo> resourceLookup = serverResourceLookup.get(serverConfig.getUniqueServerConfigName());

      ArrayList<ResourceInfo> resources = new ArrayList<>();

      FieldDefinition fieldDefinition = new FieldDefinition();
      resources.add(fieldDefinition);
      fieldDefinition.addResources(resources);
      resourceLookup.put(fieldDefinition.getResourceName(), fieldDefinition);

      if (metadata != null) {
    
        DefinitionBuilder definitionBuilder = new DefinitionBuilder(new StringReader(metadata));
        List<ResourceInfo> loadedResources = definitionBuilder.readReport();

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

      OData odata = OData.newInstance();
      ServiceMetadata edm = odata.createServiceMetadata(edmProvider, new ArrayList<EdmxReference>());

      // Create handler
      ODataHttpHandler handler = odata.createHandler(edm);

      // Register processors
      GenericEntityCollectionProcessor entityCollectionProcessor = new GenericEntityCollectionProcessor(mongoClient);
      GenericEntityProcessor entityProcessor = new GenericEntityProcessor(mongoClient, resourceLookup);

      handler.register(entityCollectionProcessor);
      handler.register(entityProcessor);

      for (ResourceInfo resource : resources) {
        LOG.info("Resource importing for client {}: {}", clientId, resource.getResourceName());
        edmProvider.addDefinition(resource);

        entityCollectionProcessor.addResource(resource, resource.getResourceName());
        entityProcessor.addResource(resource, resource.getResourceName());
      }

       ODataRequest request = new ODataRequest();
            request.setRawODataPath("/$metadata");
            request.setMethod(HttpMethod.GET);
            request.setProtocol("HTTP/1.1");
            handler.process(request);
      LOG.info("Successfully created handler for certification report {} with {} resources", serverConfig.getUniqueServerConfigName(), resources.size());
      return handler;

    } catch (Exception e) {
      LOG.error("Error creating handler for certification report: " + serverConfig.getUniqueServerConfigName(), e);
      throw new ServletException("Failed to create handler for certification report: " + serverConfig.getUniqueServerConfigName(), e);
    }

  }
     public static Map<String, ResourceInfo> getResourceLookupForServer(ServerConfig serverConfig) {
        return serverResourceLookup.getOrDefault(serverConfig.getUniqueServerConfigName(), new HashMap<>());
    }
}
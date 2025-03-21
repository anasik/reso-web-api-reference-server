package org.reso.service.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/**
 * Service responsible for managing tenant configurations.
 * Loads tenant configurations from MongoDB and provides methods for retrieving them.
 */
public class TenantConfigurationService {
    private static final Logger LOG = LoggerFactory.getLogger(TenantConfigurationService.class);
    private static final String DEFAULT_TENANT_ID = "default";
    private static final Map<String, TenantConfig> tenantConfigCache = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Check if initialized to prevent multiple initializations
    private static volatile boolean initialized = false;
    
    // MongoDB collection name for tenant configurations
    private static final String TENANT_COLLECTION = "tenant_configs";
    
    /**
     * Initialize the tenant configuration service.
     * This method loads all tenant configurations from MongoDB.
     * 
     * @param mongoClient The MongoDB client
     */
    public static synchronized void initialize(MongoClient mongoClient) {
        if (initialized) {
            LOG.info("TenantConfigurationService already initialized");
            return;
        }
        
        LOG.info("Initializing TenantConfigurationService");
        loadAllTenantConfigs(mongoClient);
        initialized = true;
    }
    
    /**
     * Load all tenant configurations from MongoDB
     * 
     * @param mongoClient The MongoDB client
     */
    private static void loadAllTenantConfigs(MongoClient mongoClient) {
        try {
            // Get the tenant configurations collection
            MongoCollection<Document> tenantCollection = mongoClient
                    .getDatabase("reso") // You might want to make this configurable
                    .getCollection(TENANT_COLLECTION);
            
            // Query for all active tenant configurations
            List<Document> tenantDocs = StreamSupport.stream(
                    tenantCollection.find(Filters.eq("isActive", true)).spliterator(), false)
                    .toList();
            
            LOG.info("Found {} active tenant configurations", tenantDocs.size());
            
            // Convert documents to TenantConfig objects and cache them
            for (Document doc : tenantDocs) {
                try {
                    String jsonString = doc.toJson();
                    TenantConfig config = objectMapper.readValue(jsonString, TenantConfig.class);
                    tenantConfigCache.put(config.getTenantId(), config);
                    LOG.info("Loaded tenant configuration for tenant: {}", config.getTenantId());
                } catch (IOException e) {
                    LOG.error("Error deserializing tenant configuration: {}", e.getMessage(), e);
                }
            }
            
            // Create default configuration if none exists
            if (tenantConfigCache.isEmpty()) {
                createDefaultTenantConfig();
            }
            
        } catch (Exception e) {
            LOG.error("Error loading tenant configurations: {}", e.getMessage(), e);
            // Fall back to default configuration
            createDefaultTenantConfig();
        }
    }
    
    /**
     * Create a default tenant configuration from environment variables
     */
    private static void createDefaultTenantConfig() {
        LOG.info("Creating default tenant configuration");
        Map<String, String> env = System.getenv();
        
        String metadataFile = env.getOrDefault("CERT_REPORT_FILENAME", "RESODataDictionary-1.7.metadata-report.json");
        String lookupType = env.getOrDefault("LOOKUP_TYPE", "STRING");
        
        TenantConfig defaultConfig = new TenantConfig(DEFAULT_TENANT_ID, metadataFile, lookupType);
        defaultConfig.setFriendlyName("Default Configuration");
        defaultConfig.setDescription("Auto-generated default configuration from environment variables");
        
        tenantConfigCache.put(DEFAULT_TENANT_ID, defaultConfig);
    }
    
    /**
     * Get a tenant configuration by ID
     * 
     * @param tenantId The tenant ID
     * @return The tenant configuration, or the default configuration if not found
     */
    public static TenantConfig getTenantConfig(String tenantId) {
        // If tenant ID is null or empty, use default
        if (tenantId == null || tenantId.isEmpty()) {
            return tenantConfigCache.get(DEFAULT_TENANT_ID);
        }
        
        // Return the requested tenant config or fall back to default
        return tenantConfigCache.getOrDefault(tenantId, tenantConfigCache.get(DEFAULT_TENANT_ID));
    }
    
    /**
     * Get all tenant configurations
     * 
     * @return A list of all tenant configurations
     */
    public static List<TenantConfig> getAllTenantConfigs() {
        return new ArrayList<>(tenantConfigCache.values());
    }
    
    /**
     * Refresh a specific tenant configuration from the database
     * 
     * @param mongoClient The MongoDB client
     * @param tenantId The tenant ID to refresh
     * @return The refreshed tenant configuration
     */
    public static TenantConfig refreshTenantConfig(MongoClient mongoClient, String tenantId) {
        try {
            MongoCollection<Document> tenantCollection = mongoClient
                    .getDatabase("reso")
                    .getCollection(TENANT_COLLECTION);
            
            Document doc = tenantCollection.find(Filters.eq("tenantId", tenantId)).first();
            if (doc != null) {
                TenantConfig config = objectMapper.readValue(doc.toJson(), TenantConfig.class);
                tenantConfigCache.put(tenantId, config);
                LOG.info("Refreshed tenant configuration for tenant: {}", tenantId);
                return config;
            }
        } catch (Exception e) {
            LOG.error("Error refreshing tenant configuration: {}", e.getMessage(), e);
        }
        
        return tenantConfigCache.get(tenantId);
    }
    
    /**
     * Check if the service is initialized
     * 
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Clear all cached tenant configurations and mark as uninitialized
     */
    public static synchronized void reset() {
        tenantConfigCache.clear();
        initialized = false;
    }
} 
package org.reso.service.tenant;

import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for ODataHttpHandlers by tenant.
 * This prevents having to recreate handlers for every request.
 */
public class ODataHandlerCache {
    private static final Logger LOG = LoggerFactory.getLogger(ODataHandlerCache.class);
    private static final Map<String, CachedHandler> handlers = new ConcurrentHashMap<>();
    
    // Maximum number of handlers to keep in cache (LRU eviction)
    private static final int MAX_CACHE_SIZE = 100;
    
    // Time-to-live for cached handlers in milliseconds (1 hour)
    private static final long TTL = 60 * 60 * 1000;
    
    /**
     * Inner class to hold a cached handler with its timestamp
     */
    private static class CachedHandler {
        final ODataHttpHandler handler;
        final long timestamp;
        
        CachedHandler(ODataHttpHandler handler) {
            this.handler = handler;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TTL;
        }
    }
    
    /**
     * Get a handler from the cache or return null if not found or expired
     * 
     * @param tenantId The tenant ID
     * @return The cached handler or null
     */
    public static ODataHttpHandler getHandler(String tenantId) {
        CachedHandler cached = handlers.get(tenantId);
        if (cached != null) {
            if (cached.isExpired()) {
                LOG.debug("Handler for tenant {} has expired, removing from cache", tenantId);
                handlers.remove(tenantId);
                return null;
            }
            LOG.debug("Using cached handler for tenant {}", tenantId);
            return cached.handler;
        }
        return null;
    }
    
    /**
     * Store a handler in the cache
     * 
     * @param tenantId The tenant ID
     * @param odata The OData instance
     * @param serviceMetadata The service metadata
     * @return The cached handler
     */
    public static ODataHttpHandler cacheHandler(String tenantId, OData odata, ServiceMetadata serviceMetadata) {
        // Check if cache is full and evict oldest entry if necessary
        if (handlers.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }
        
        ODataHttpHandler handler = odata.createHandler(serviceMetadata);
        handlers.put(tenantId, new CachedHandler(handler));
        LOG.debug("Cached handler for tenant {}", tenantId);
        
        return handler;
    }
    
    /**
     * Remove a handler from the cache
     * 
     * @param tenantId The tenant ID
     */
    public static void invalidate(String tenantId) {
        handlers.remove(tenantId);
        LOG.debug("Removed handler for tenant {} from cache", tenantId);
    }
    
    /**
     * Clear the entire cache
     */
    public static void invalidateAll() {
        handlers.clear();
        LOG.info("Cleared entire handler cache");
    }
    
    /**
     * Evict the oldest handler from the cache
     */
    private static void evictOldest() {
        String oldestTenantId = null;
        long oldestTimestamp = Long.MAX_VALUE;
        
        for (Map.Entry<String, CachedHandler> entry : handlers.entrySet()) {
            if (entry.getValue().timestamp < oldestTimestamp) {
                oldestTimestamp = entry.getValue().timestamp;
                oldestTenantId = entry.getKey();
            }
        }
        
        if (oldestTenantId != null) {
            handlers.remove(oldestTenantId);
            LOG.debug("Evicted oldest handler for tenant {} from cache", oldestTenantId);
        }
    }
} 
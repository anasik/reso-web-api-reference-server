package org.reso.service.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-local context for storing the current tenant ID.
 * This allows tenant information to be accessible throughout the request lifecycle.
 */
public class TenantContext {
    private static final Logger LOG = LoggerFactory.getLogger(TenantContext.class);
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    private TenantContext() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Set the current tenant ID for this thread
     * 
     * @param tenantId The tenant ID
     */
    public static void setCurrentTenant(String tenantId) {
        LOG.debug("Setting current tenant to: {}", tenantId);
        currentTenant.set(tenantId);
    }
    
    /**
     * Get the current tenant ID for this thread
     * 
     * @return The current tenant ID, or null if not set
     */
    public static String getCurrentTenant() {
        String tenantId = currentTenant.get();
        if (tenantId == null) {
            LOG.debug("Current tenant not set, defaulting to 'default'");
            return "default";
        }
        return tenantId;
    }
    
    /**
     * Clear the current tenant ID for this thread
     */
    public static void clear() {
        LOG.debug("Clearing current tenant");
        currentTenant.remove();
    }
} 
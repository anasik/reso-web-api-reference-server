package org.reso.service.tenant;

import java.util.Objects;

/**
 * Configuration for a specific tenant in the multi-tenant application.
 * Each tenant has its own metadata file, lookup type, and other settings.
 */
public class TenantConfig {
    private String tenantId;
    private String metadataFilePath;
    private String lookupType;
    private String friendlyName;
    private String description;
    private boolean isActive = true;
    
    // Default constructor for deserialization
    public TenantConfig() {
    }
    
    public TenantConfig(String tenantId, String metadataFilePath, String lookupType) {
        this.tenantId = tenantId;
        this.metadataFilePath = metadataFilePath;
        this.lookupType = lookupType;
    }
    
    // Getters and setters
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getMetadataFilePath() {
        return metadataFilePath;
    }
    
    public void setMetadataFilePath(String metadataFilePath) {
        this.metadataFilePath = metadataFilePath;
    }
    
    public String getLookupType() {
        return lookupType;
    }
    
    public void setLookupType(String lookupType) {
        this.lookupType = lookupType;
    }
    
    public String getFriendlyName() {
        return friendlyName;
    }
    
    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantConfig that = (TenantConfig) o;
        return Objects.equals(tenantId, that.tenantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }
    
    @Override
    public String toString() {
        return "TenantConfig{" +
                "tenantId='" + tenantId + '\'' +
                ", metadataFilePath='" + metadataFilePath + '\'' +
                ", lookupType='" + lookupType + '\'' +
                ", friendlyName='" + friendlyName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
} 
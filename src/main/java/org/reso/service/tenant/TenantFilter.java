package org.reso.service.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that extracts the tenant ID from the request.
 * It supports extraction from subdomain, path, or header.
 */
public class TenantFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(TenantFilter.class);
    
    // Pattern to match tenant ID in path (e.g., /api/tenantId/...)
    private static final Pattern PATH_PATTERN = Pattern.compile("/api/([^/]+).*");
    
    // Tenant ID header name
    private static final String TENANT_HEADER = "X-Tenant-ID";
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        LOG.info("Initializing TenantFilter");
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenantId = resolveTenantId(httpRequest);
        
        try {
            // Set the tenant ID in the context
            TenantContext.setCurrentTenant(tenantId);
            
            // Continue with the request
            chain.doFilter(request, response);
        } finally {
            // Clear the tenant context after the request is processed
            TenantContext.clear();
        }
    }
    
    @Override
    public void destroy() {
        LOG.info("Destroying TenantFilter");
    }
    
    /**
     * Resolve the tenant ID from the request.
     * Checks for tenant ID in the following order:
     * 1. Header (X-Tenant-ID)
     * 2. Path parameter (/api/{tenantId}/...)
     * 3. Subdomain (tenantId.example.com)
     * 
     * @param request The HTTP request
     * @return The tenant ID, or "default" if not found
     */
    private String resolveTenantId(HttpServletRequest request) {
        // 1. Check header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isEmpty()) {
            LOG.debug("Resolved tenant ID from header: {}", tenantId);
            return tenantId;
        }
        
        // 2. Check path
        String path = request.getRequestURI();
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (matcher.matches()) {
            tenantId = matcher.group(1);
            LOG.debug("Resolved tenant ID from path: {}", tenantId);
            return tenantId;
        }
        
        // 3. Check subdomain
        String host = request.getServerName();
        if (host.contains(".")) {
            tenantId = host.substring(0, host.indexOf('.'));
            LOG.debug("Resolved tenant ID from subdomain: {}", tenantId);
            return tenantId;
        }
        
        // Default tenant ID
        LOG.debug("Could not resolve tenant ID, using default");
        return "default";
    }
} 
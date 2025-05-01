package org.reso.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.reso.service.servlet.util.SimpleError;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public class JWTAuthenticationFilter implements Filter {
    private Key signingKey;
    private ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new ServletException("JWT_SECRET environment variable not set");
        }
        signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String authHeader = httpReq.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            SimpleError error = new SimpleError(SimpleError.AUTH_REQUIRED);
            PrintWriter out = httpResp.getWriter();
            out.print(objectMapper.writeValueAsString(error));
            return;
        }
        
        String token = authHeader.substring(7); // remove "Bearer " prefix
        try {
            Jws<Claims> jwsClaims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token);
            Claims claims = jwsClaims.getBody();
            // ensure the token isn’t expired.
            if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
                httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                SimpleError error = new SimpleError(SimpleError.TOKEN_EXPIRED);
                PrintWriter out = httpResp.getWriter();
                out.print(objectMapper.writeValueAsString(error));
                return;
            }
            // Extract the client id from the JWT payload.
            String clientId = claims.get("client_id", String.class);
            httpReq.setAttribute("client_id", clientId);
        } catch (JwtException e) {
            httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            SimpleError error = new SimpleError("Invalid token: " + e.getMessage());
            httpResp.setContentType("application/json");
            PrintWriter out = httpResp.getWriter();
            out.print(objectMapper.writeValueAsString(error));
            return;
        }
        
        chain.doFilter(request, response);
    }
    
}
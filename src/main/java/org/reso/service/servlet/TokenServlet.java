package org.reso.service.servlet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.bson.Document;
import org.reso.service.data.mongodb.MongoDBManager;
import org.reso.service.servlet.util.SimpleError;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

public class TokenServlet extends HttpServlet {

  private Key signingKey;
  private MongoCollection<Document> clientsCollection;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void init() throws ServletException {
    clientsCollection = MongoDBManager.getDatabase().getCollection("clients");
    
    String jwtSecret = System.getenv("JWT_SECRET");
    if (jwtSecret == null || jwtSecret.isEmpty()) {
        throw new ServletException("JWT_SECRET environment variable not set");
    }
    signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  protected void service(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    if (!"POST".equals(request.getMethod())) {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }
    super.service(request, response);
  }

  @Override
  protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException, ServletException {

    PrintWriter out = response.getWriter();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> body;

    try {
      body = mapper.readValue(request.getReader(), new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format");
      return;
    }

    String clientId = body.get("client_id");
    String clientSecret = body.get("client_secret");
    String refreshToken = body.get("refresh_token");

    // Validate the presence of the credentials
    if (clientId == null || clientSecret == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Missing client_id and/or client_secret in the request body");
      return;
    }

    if (refreshToken != null) {
      refreshTokenHandler(refreshToken, clientSecret, response);
    }

    Document clientDoc = clientsCollection.find(new Document("clientId", clientId)
        .append("secret", clientSecret)).first();
        
    if (clientDoc == null) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      SimpleError error = new SimpleError(SimpleError.INVALID_REQUEST);
      out.print(objectMapper.writeValueAsString(error));
      return;
    }
    boolean singleSession = clientDoc.getBoolean("singleSession", false);

    // Generate the access token and refresh token.
    String accessToken = generateAccessToken(clientId, singleSession);
    String genereatedRefreshToken = generateRefreshToken(clientId, singleSession);

    Map<String, String> tokenResponse = new HashMap<>();
    tokenResponse.put("access_token", accessToken);
    tokenResponse.put("refresh_token", genereatedRefreshToken);
    tokenResponse.put("token_type", "bearer");

    out.print(objectMapper.writeValueAsString(tokenResponse));
    out.flush();
  }

  private String generateAccessToken(String clientId, boolean singleSession) {
    JwtBuilder builder = Jwts.builder()
        .claim("client_id", clientId)
        .setIssuedAt(new Date());
    if (!singleSession) {
      Date exp = new Date(System.currentTimeMillis() + 24 * 3600 * 1000); // 24 hours
      builder.setExpiration(exp);
    }
    return builder.signWith(signingKey, SignatureAlgorithm.HS256).compact();
  }

  private String generateRefreshToken(String clientId, boolean singleSession) {
    JwtBuilder builder = Jwts.builder()
        .claim("client_id", clientId)
        .claim("type", "refresh")
        .setIssuedAt(new Date());
    if (!singleSession) {
      Date exp = new Date(System.currentTimeMillis() + 7 * 24 * 3600 * 1000); // 7 days
      builder.setExpiration(exp);
    }
    return builder.signWith(signingKey, SignatureAlgorithm.HS256).compact();
  }

  private void refreshTokenHandler(String refreshToken, String clientSecret, HttpServletResponse response) throws IOException{
    PrintWriter out = response.getWriter();
    try {
      Jws<Claims> jwsClaims = Jwts.parserBuilder()
          .setSigningKey(signingKey)
          .build()
          .parseClaimsJws(refreshToken);
      Claims claims = jwsClaims.getBody();
      // Ensure the token is flagged as a refresh token.
      if (!"refresh".equals(claims.get("type", String.class))) {
        throw new JwtException("Invalid token type");
      }
      // Extract the client id from the token
      String tokenClientId = claims.get("clientId", String.class);
      // validate the client in the database.
      Document clientDoc = clientsCollection.find(
          new Document("clientId", tokenClientId)
              .append("secret", clientSecret))
          .first();
      if (clientDoc == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        SimpleError error = new SimpleError(SimpleError.INVALID_REQUEST);
        out.print(objectMapper.writeValueAsString(error));
        return;
      }
      boolean singleSession = clientDoc.getBoolean("singleSession", false);
      // Generate new access and refresh tokens.
      String newAccessToken = generateAccessToken(tokenClientId, singleSession);
      String newRefreshToken = generateRefreshToken(tokenClientId, singleSession);
      Map<String, String> tokenResponse = new HashMap<>();
      tokenResponse.put("access_token", newAccessToken);
      tokenResponse.put("refresh_token", newRefreshToken);
      tokenResponse.put("token_type", "bearer");
      out.print(objectMapper.writeValueAsString(tokenResponse));
      return;

    } catch (JwtException e) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      SimpleError error = new SimpleError("Invalid refresh token");
      out.print(objectMapper.writeValueAsString(error));
      return;
    }
  }
}
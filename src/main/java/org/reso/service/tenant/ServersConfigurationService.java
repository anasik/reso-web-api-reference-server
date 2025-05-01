package org.reso.service.tenant;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.reso.service.data.mongodb.MongoDBManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServersConfigurationService {

  // Maximum number of configuration entries in the cache
  private static final int MAX_CACHE_SIZE = 1000;

  private static final Map<String, ServerConfig> serverConfigCache = Collections.synchronizedMap(
      new LinkedHashMap<String, ServerConfig>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ServerConfig> eldest) {
          return size() > MAX_CACHE_SIZE;
        }
      });

  public static ServerConfig getServerConfig(String clientId) {
    ServerConfig config = serverConfigCache.get(clientId);
    if (config != null) {
      return config;
    }
    MongoDatabase database = MongoDBManager.getDatabase();
    MongoCollection<Document> collection = database.getCollection("sanbox_server_configurations");
    Document query = new Document("clientId", clientId);
    Document doc = collection.find(query).first();

    if (doc == null) {
      return null;
    }

    config = new ServerConfig();
    config.setClientId(doc.getString("clientId"));
    config.setSandboxServerId(doc.getString("sandboxSeverId"));
    config.setCertificationReportId(doc.getString("certificationReportId"));
    config.setAuthenticationType(doc.getString("authenticationType"));
    config.setLookupType(doc.getString("lookupType"));

    List<String> endorsements = (List<String>) doc.get("additionalEndorsements");
    config.setAdditionalEndorsements(endorsements);

    serverConfigCache.put(clientId, config);
    return config;
  }

}
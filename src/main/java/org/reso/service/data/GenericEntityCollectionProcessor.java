package org.reso.service.data;

import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.reso.service.data.common.CommonDataProcessing;
import org.reso.service.data.meta.FieldInfo;
import org.reso.service.data.meta.MongoDBFilterExpressionVisitor;
import org.reso.service.data.meta.MySQLFilterExpressionVisitor;
import org.reso.service.data.meta.PostgreSQLFilterExpressionVisitor;
import org.reso.service.data.meta.ResourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.MongoClient;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.reso.service.servlet.RESOservlet.resourceLookup;

public class GenericEntityCollectionProcessor implements EntityCollectionProcessor {
   private OData odata;
   private ServiceMetadata serviceMetadata;
   private final MongoClient mongoClient;
   private Connection connect;
   private String dbType;
   HashMap<String, ResourceInfo> resourceList = null;
   private static final Logger LOG = LoggerFactory.getLogger(GenericEntityCollectionProcessor.class);
   private static final int PAGE_SIZE = 100;
   private static final Map<String, NavigationConfig> NAVIGATION_CONFIGS = new HashMap<>();

   static {
      // Property navigation properties
      addNavConfig("Property", "BuyerAgent", "member", "BuyerAgentKey", "MemberKey", false);
      addNavConfig("Property", "BuyerOffice", "office", "BuyerOfficeKey", "OfficeKey", false);
      addNavConfig("Property", "BuyerTeam", "teams", "BuyerTeamKey", "TeamKey", false);
      addNavConfig("Property", "CoBuyerAgent", "member", "CoBuyerAgentKey", "MemberKey", false);
      addNavConfig("Property", "CoBuyerOffice", "office", "CoBuyerOfficeKey", "OfficeKey", false);
      addNavConfig("Property", "CoListAgent", "member", "CoListAgentKey", "MemberKey", false);
      addNavConfig("Property", "CoListOffice", "office", "CoListOfficeKey", "OfficeKey", false);
      addNavConfig("Property", "ListAgent", "member", "ListAgentKey", "MemberKey", false);
      addNavConfig("Property", "ListOffice", "office", "ListOfficeKey", "OfficeKey", false);
      addNavConfig("Property", "ListTeam", "teams", "ListTeamKey", "TeamKey", false);

      // Resource record based navigation properties
      addNavConfig("Property", "Media", "media", "ResourceRecordKey,ResourceName", null, true);
      addNavConfig("Property", "SocialMedia", "socialmedia", "ResourceRecordKey,ResourceName", null, true);
      addNavConfig("Property", "HistoryTransactional", "historytransactional", "ResourceRecordKey,ResourceName", null,
            true);

      // ListingKey based navigation properties
      addNavConfig("Property", "GreenBuildingVerification", "propertygreenverification", "ListingKey", "ListingKey",
            true);
      addNavConfig("Property", "OpenHouse", "openhouse", "ListingKey", "ListingKey", true);
      addNavConfig("Property", "PowerProduction", "propertypowerproduction", "ListingKey", "ListingKey", true);
      addNavConfig("Property", "Rooms", "propertyrooms", "ListingKey", "ListingKey", true);
      addNavConfig("Property", "UnitTypes", "propertyunittypes", "ListingKey", "ListingKey", true);

      // OUID navigation properties
      addNavConfig("Property", "OriginatingSystem", "ouid", "OriginatingSystemID", "OrganizationUniqueId", false);
      addNavConfig("Property", "SourceSystem", "ouid", "SourceSystemID", "OrganizationUniqueId", false);

      // Add more configurations for other resources...
      // Member navigation properties
      addNavConfig("Member", "Office", "office", "OfficeKey", "OfficeKey", false);
      addNavConfig("Member", "Media", "media", "ResourceRecordKey,ResourceName", null, true);
      addNavConfig("Member", "MemberSocialMedia", "socialmedia", "ResourceRecordKey,ResourceName", null, true);
      addNavConfig("Member", "HistoryTransactional", "historytransactional", "ResourceRecordKey,ResourceName", null,
            true);

      // Office navigation properties
      addNavConfig("Office", "MainOffice", "office", "MainOfficeKey", "OfficeKey", false);
      addNavConfig("Office", "OfficeBroker", "member", "OfficeBrokerKey", "MemberKey", false);
      addNavConfig("Office", "OfficeManager", "member", "OfficeManagerKey", "MemberKey", false);
      addNavConfig("Office", "Listings", "property", "OfficeKey", "ListOfficeKey", true);
      addNavConfig("Office", "Agents", "member", "OfficeKey", "OfficeKey", true);
   }

   private static void addNavConfig(String sourceResource, String navProperty, String targetCollection,
         String sourceKey, String targetKey, boolean isCollection) {
      NAVIGATION_CONFIGS.put(sourceResource + "." + navProperty,
            new NavigationConfig(targetCollection, sourceKey, targetKey, isCollection));
   }

   private static class NavigationConfig {
      final String targetCollection;
      final String sourceKey;
      final String targetKey;
      final boolean isCollection;

      NavigationConfig(String targetCollection, String sourceKey, String targetKey, boolean isCollection) {
         this.targetCollection = targetCollection;
         this.sourceKey = sourceKey;
         this.targetKey = targetKey;
         this.isCollection = isCollection;
      }
   }

   public GenericEntityCollectionProcessor(MongoClient mongoClient) {
      this.mongoClient = mongoClient;
      this.dbType = System.getenv().getOrDefault("DB_TYPE", "mongodb").toLowerCase();
      try {
         if (!"mongodb".equals(this.dbType)) {
            String jdbcUrl = System.getenv().getOrDefault("JDBC_URL", "");
            String username = System.getenv().getOrDefault("DB_USERNAME", "");
            String password = System.getenv().getOrDefault("DB_PASSWORD", "");
            this.connect = DriverManager.getConnection(jdbcUrl, username, password);
         }
      } catch (SQLException e) {
         LOG.error("Failed to establish database connection", e);
      }
      this.resourceList = new HashMap<>();
   }

   public void init(OData odata, ServiceMetadata serviceMetadata) {
      this.odata = odata;
      this.serviceMetadata = serviceMetadata;
   }

   public void addResource(ResourceInfo resource, String name) {
      resourceList.put(name, resource);
   }

   public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo,
         ContentType responseFormat)
         throws ODataApplicationException, SerializerException {

      // 1st we have retrieve the requested EntitySet from the uriInfo object
      // (representation of the parsed service URI)
      List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
      UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0); // in our example, the
                                                                                               // first segment is the
                                                                                               // EntitySet
      EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

      String resourceName = uriResourceEntitySet.toString();

      ResourceInfo resource = this.resourceList.get(resourceName);

      boolean isCount = false;
      CountOption countOption = uriInfo.getCountOption();
      if (countOption != null) {
         isCount = countOption.getValue();
         if (isCount) {
            LOG.debug("Count str:" + countOption.getText());
         }
      }

      // 2nd: fetch the data from backend for this requested EntitySetName
      // it has to be delivered as EntitySet object
      EntityCollection entitySet;

      if (resource.useCustomDatasource()) {
         entitySet = resource.getData(edmEntitySet, uriInfo, isCount);
      } else {
         entitySet = getData(edmEntitySet, uriInfo, isCount, resource);
      }

      TopOption topOption = uriInfo.getTopOption();
      SkipOption skipOption = uriInfo.getSkipOption();
      int topNumber = topOption == null ? PAGE_SIZE : topOption.getValue();
      int skipNumber = skipOption == null ? 0 : skipOption.getValue();

      // 3rd: create a serializer based on the requested format (json)
      try {
         entitySet.setNext(new URI(modifyUrl(request.getRawRequestUri(), topNumber, skipNumber + topNumber)));
         uriInfo.asUriInfoAll().getFormatOption().getFormat(); // If Format is given, then we will use what it has.
      } catch (Exception e) {
         responseFormat = ContentType.JSON; // If format is not set in the $format, then use JSON.
         // There is some magic that will select XML if you're viewing from a browser or
         // something which I'm bypassing here.
         // If you want a different $format, explicitly state it.
      }

      ODataSerializer serializer = odata.createSerializer(responseFormat);

      // 4th: Now serialize the content: transform from the EntitySet object to
      // InputStream
      EdmEntityType edmEntityType = edmEntitySet.getEntityType();
      SelectOption selectOption = uriInfo.getSelectOption();
      ExpandOption expandOption = uriInfo.getExpandOption();
      String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType,
            expandOption, selectOption);
      ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).selectList(selectList).build();

      final String id = request.getRawBaseUri() + "/" + edmEntitySet.getName();
      EntityCollectionSerializerOptions opts = null;
      if (isCount) // If there's a $count=true in the query string, we need to have a different
                   // formatting options.
      {
         opts = EntityCollectionSerializerOptions.with()
               .contextURL(contextUrl)
               .id(id)
               .count(countOption)
               .build();
      } else {
         if (selectOption != null) {
            opts = EntityCollectionSerializerOptions.with()
                  .contextURL(contextUrl)
                  .select(selectOption).expand(expandOption)
                  .id(id)
                  .build();
         } else {
            opts = EntityCollectionSerializerOptions.with().id(id).contextURL(contextUrl).expand(expandOption).build();
         }
      }
      SerializerResult serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, entitySet, opts);
      InputStream serializedContent = serializerResult.getContent();

      // Finally: configure the response object: set the body, headers and status code
      response.setContent(serializedContent);
      response.setStatusCode(HttpStatusCode.OK.getStatusCode());
      response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
   }

   protected EntityCollection getData(EdmEntitySet edmEntitySet, UriInfo uriInfo, boolean isCount,
         ResourceInfo resource) throws ODataApplicationException {
      String dbType = getDatabaseType();
      if ("mongodb".equals(dbType)) {
         return getDataFromMongo(edmEntitySet, uriInfo, isCount, resource);
      } else {
         return getDataFromSQL(edmEntitySet, uriInfo, isCount, resource);
      }
   }

   protected EntityCollection getDataFromMongo(EdmEntitySet edmEntitySet, UriInfo uriInfo, boolean isCount,
         ResourceInfo resource) throws ODataApplicationException {
      EntityCollection dataCollection = new EntityCollection();

      try {
         // Log MongoDB client state
         LOG.info("MongoDB client status - isNull: {}", mongoClient == null);

         // Initialize with empty filter
         Document filter = new Document();

         FilterOption filterOption = uriInfo.getFilterOption();
         if (filterOption != null) {
            String filterExpr = filterOption.getExpression()
                  .accept(new MongoDBFilterExpressionVisitor(resource));
            filter = Document.parse(filterExpr);
            LOG.info("Applied filter expression: {}", filterExpr);
         }

         TopOption topOption = uriInfo.getTopOption();
         SkipOption skipOption = uriInfo.getSkipOption();
         int topNumber = topOption == null ? PAGE_SIZE : topOption.getValue();
         int skipNumber = skipOption == null ? 0 : skipOption.getValue();

         LOG.info("Pagination - top: {}, skip: {}", topNumber, skipNumber);

         if (isCount) {
            int count = resource.executeMongoCount(filter);
            dataCollection.setCount(count);
            LOG.info("Count query result: {}", count);
         } else {
            // Get the base data collection
            MongoDatabase database = mongoClient.getDatabase("reso");
            String collectionName = resource.getTableName().toLowerCase();
            LOG.info("Attempting to access collection: {}", collectionName);

            // List all collections in the database
            LOG.info("Available collections in database:");
            database.listCollectionNames().into(new ArrayList<>()).forEach(name -> LOG.info("- Collection: {}", name));

            MongoCollection<Document> collection = database.getCollection(collectionName);
            LOG.info("Collection stats: count={}", collection.countDocuments());

            LOG.info("Executing MongoDB query on collection: {} with filter: {}",
                  collection.getNamespace(),
                  filter.toJson());

            FindIterable<Document> findIterable = collection.find(filter)
                  .skip(skipNumber)
                  .limit(topNumber)
                  .maxTime(5000, TimeUnit.MILLISECONDS);

            // Execute query and build collection
            try (MongoCursor<Document> cursor = findIterable.iterator()) {
               int documentCount = 0;
               while (cursor.hasNext()) {
                  Document doc = cursor.next();
                  documentCount++;
                  LOG.info("Found document {}: {}", documentCount, doc.toJson());
                  Entity entity = CommonDataProcessing.getEntityFromDocument(doc, resource);
                  dataCollection.getEntities().add(entity);
               }
               LOG.info("Total documents processed: {}", documentCount);
            }

            LOG.info("Retrieved {} documents from MongoDB", dataCollection.getEntities().size());
         }

         // Handle $expand if present
         ExpandOption expandOption = uriInfo.getExpandOption();
         if (expandOption != null && !dataCollection.getEntities().isEmpty()) {
            handleMongoExpand(dataCollection, resource, expandOption);
         }
      } catch (Exception e) {
         LOG.error("Error executing MongoDB query: {}", e.getMessage(), e);
         LOG.error("Stack trace:", e);
         throw new ODataApplicationException("Error executing MongoDB query: " + e.getMessage(),
               HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
      }

      return dataCollection;
   }

   private void handleMongoExpand(EntityCollection sourceCollection, ResourceInfo sourceResource,
         ExpandOption expandOption) {
      if (mongoClient == null) {
         LOG.error("MongoDB client is not initialized");
         return;
      }

      try {
         MongoDatabase database = mongoClient.getDatabase("reso");

         for (Entity sourceEntity : sourceCollection.getEntities()) {
            for (ExpandItem expandItem : expandOption.getExpandItems()) {
               UriResource expandPath = expandItem.getResourcePath().getUriResourceParts().get(0);
               if (!(expandPath instanceof UriResourceNavigation)) {
                  continue;
               }

               UriResourceNavigation expandNavigation = (UriResourceNavigation) expandPath;
               String navPropertyName = expandNavigation.getProperty().getName();
               String configKey = sourceResource.getResourceName() + "." + navPropertyName;

               NavigationConfig config = NAVIGATION_CONFIGS.get(configKey);
               if (config == null) {
                  LOG.warn("Unsupported navigation property: {}", navPropertyName);
                  continue;
               }

               Document query = new Document();
               if (config.sourceKey.contains(",")) {
                  // Handle composite keys (ResourceRecordKey,ResourceName)
                  String[] keys = config.sourceKey.split(",");
                  if (keys[0].equals("ResourceRecordKey")) {
                     Property keyProp = sourceEntity.getProperty(sourceResource.getPrimaryKeyName());
                     if (keyProp != null && keyProp.getValue() != null) {
                        query.append("ResourceRecordKey", keyProp.getValue().toString());
                        query.append("ResourceName", sourceResource.getResourceName());
                     }
                  }
               } else {
                  // Handle single key relationship
                  Property sourceProp = sourceEntity.getProperty(config.sourceKey);
                  if (sourceProp != null && sourceProp.getValue() != null) {
                     String keyValue = sourceProp.getValue().toString();
                     if (config.targetKey != null) {
                        query.append(config.targetKey, keyValue);
                     } else {
                        query.append(config.sourceKey, keyValue);
                     }
                  }
               }

               if (query.isEmpty()) {
                  LOG.warn("No key found for navigation property: {}", navPropertyName);
                  continue;
               }

               LOG.info("Querying {} with filter: {}", config.targetCollection, query.toJson());
               MongoCollection<Document> collection = database.getCollection(config.targetCollection);
               EntityCollection expandEntities = new EntityCollection();

               try (MongoCursor<Document> cursor = collection.find(query)
                     .maxTime(5000, TimeUnit.MILLISECONDS)
                     .iterator()) {
                  while (cursor.hasNext()) {
                     Document doc = cursor.next();
                     LOG.info("Found {} document: {}", navPropertyName, doc.toJson());

                     // Determine the actual resource name based on the collection
                     String resourceName = config.targetCollection;
                     if (config.targetCollection.equals("member")) {
                        resourceName = "Member";
                     } else if (config.targetCollection.equals("office")) {
                        resourceName = "Office";
                     } else {
                        resourceName = navPropertyName;
                     }

                     ResourceInfo expandResource = resourceLookup.get(resourceName);
                     if (expandResource == null) {
                        LOG.error("Resource not found for expansion: {} (looking up as {})", navPropertyName,
                              resourceName);
                        continue;
                     }
                     Entity expandEntity = CommonDataProcessing.getEntityFromDocument(doc, expandResource);
                     expandEntities.getEntities().add(expandEntity);
                  }
               }

               Link link = new Link();
               link.setTitle(navPropertyName);
               link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);

               if (!expandEntities.getEntities().isEmpty()) {
                  if (config.isCollection) {
                     link.setInlineEntitySet(expandEntities);
                     LOG.info("Added {} {} items to entity",
                           expandEntities.getEntities().size(), navPropertyName);
                  } else {
                     link.setInlineEntity(expandEntities.getEntities().get(0));
                     LOG.info("Added {} entity", navPropertyName);
                  }
               } else {
                  LOG.warn("No {} entities found", navPropertyName);
               }

               sourceEntity.getNavigationLinks().add(link);
            }
         }
      } catch (Exception e) {
         LOG.error("Error in handleMongoExpand: {}", e.getMessage(), e);
      }
   }

   protected EntityCollection getDataFromSQL(EdmEntitySet edmEntitySet, UriInfo uriInfo, boolean isCount,
         ResourceInfo resource) throws ODataApplicationException {
      ArrayList<FieldInfo> fields = resource.getFieldList();
      EntityCollection entCollection = new EntityCollection();
      List<Entity> productList = entCollection.getEntities();
      Bson mongoCriteria = null;
      try {
         String primaryFieldName = resource.getPrimaryKeyName();
         FilterOption filter = uriInfo.getFilterOption();
         String sqlCriteria = null;

         if (filter != null) {
            if (this.mongoClient == null) {
               throw new ODataApplicationException("MongoDB client not initialized",
                     HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
            }
            sqlCriteria = filter.getExpression().accept(new MongoDBFilterExpressionVisitor(resource));
         }

         HashMap<String, Boolean> selectLookup = null;
         Statement statement = connect.createStatement();
         String queryString = null;
         LOG.info("Detected Database Type: " + getDatabaseType());

         if (isCount) {
            queryString = "SELECT count(*) AS rowcount FROM " + resource.getTableName();
         } else {
            queryString = "SELECT * FROM " + resource.getTableName();
         }

         // SelectOption selectOption = uriInfo.getSelectOption();

         if (sqlCriteria != null && !sqlCriteria.isEmpty()) {
            queryString += " WHERE " + sqlCriteria;
         }

         LOG.info("Executing query in MongoDB...");

         // Pagination logic: Ensure LIMIT is only added when it's > 0
         TopOption topOption = uriInfo.getTopOption();
         int topNumber = (topOption == null) ? PAGE_SIZE : topOption.getValue();
         SkipOption skipOption = uriInfo.getSkipOption();
         int skipNumber = (skipOption != null) ? skipOption.getValue() : 0;

         LOG.debug("dbType: " + this.dbType);
         LOG.debug("Top: " + topNumber + ", Skip: " + skipNumber);

         OrderByOption orderByOption = uriInfo.getOrderByOption();
         if (orderByOption != null) {
            List<OrderByItem> orderItemList = orderByOption.getOrders();
            final OrderByItem orderByItem = orderItemList.get(0);
            Expression expression = orderByItem.getExpression();
            if (expression instanceof Member) {
               UriInfoResource resourcePath = ((Member) expression).getResourcePath();
               UriResource uriResource = resourcePath.getUriResourceParts().get(0);
               if (uriResource instanceof UriResourcePrimitiveProperty) {
                  EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
                  final String sortPropertyName = edmProperty.getName();
                  queryString += " ORDER BY " + sortPropertyName;
                  if (orderByItem.isDescending()) {
                     queryString += " DESC";
                  }
               }
            }
         }

         LOG.info("SQL Query after workaround: " + queryString);
         if (topNumber > 0) {
            if (this.dbType.equals("mysql")) {
               queryString += " LIMIT " + topNumber + ", " + skipNumber;
            } else if (this.dbType.equals("postgres")) {
               queryString += " LIMIT " + topNumber + " OFFSET " + skipNumber;
            } else {
               // Default case
               if (topNumber > 0) {
                  queryString += " LIMIT " + topNumber + " OFFSET " + skipNumber;
               }
            }
         } else {
            LOG.warn("Skipping LIMIT because topNumber is 0");
         }

         LOG.info("Final SQL Query before execution: " + queryString);
         ResultSet resultSet = statement.executeQuery(queryString);

         while (resultSet.next()) {
            Entity ent = CommonDataProcessing.getEntityFromRow(resultSet, resource, selectLookup);

            if (isCount) {
               int size = resultSet.getInt("rowcount");
               LOG.debug("Size = " + size);
               entCollection.setCount(size);
            }

            productList.add(ent);
         }
         statement.close();
      } catch (

      Exception e) {
         LOG.error("Server Error occurred in reading " + resource.getResourceName(), e);
         return entCollection;
      }

      return entCollection;
   }

   private static String modifyUrl(String url, Integer topValue, Integer skipValue) {
      url = modifyParameter(url, "\\$top=\\d+", "\\$top", topValue);
      url = modifyParameter(url, "\\$skip=\\d+", "\\$skip", skipValue);
      return url;
   }

   // Helper method to replace or append a parameter
   private static String modifyParameter(String url, String pattern, String paramName, Integer value) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(url);

      if (value != null) {
         String replacement = paramName + "=" + value;
         // Check if the parameter already exists
         if (m.find()) {
            // Replace existing parameter
            url = m.replaceFirst(replacement);
            LOG.info("1: Url Replacement: " + url);
         } else {
            // Append parameter, handling both cases: with and without existing query
            url += (url.contains("?") ? "&" : "?") + replacement.replace("\\", "");
            LOG.info("2: Url Replacement: " + url);
         }
      }

      return url;
   }

   private String getDatabaseType() {
      if (this.dbType != null) {
         return this.dbType;
      }
      try {
         if (this.connect != null) {
            String dbProductName = this.connect.getMetaData().getDatabaseProductName().toLowerCase();
            if (dbProductName.contains("mysql")) {
               return "mysql";
            } else if (dbProductName.contains("postgres")) {
               return "postgres";
            }
         }
         return "mongodb";
      } catch (SQLException e) {
         LOG.error("Error getting database type", e);
         return "mongodb";
      }
   }

}

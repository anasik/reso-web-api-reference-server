package org.reso.service.data;

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
import com.mongodb.client.model.Filters;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericEntityCollectionProcessor implements EntityCollectionProcessor {
   private OData odata;
   private ServiceMetadata serviceMetadata;
   private Connection connect = null;
   private final String dbType;
   HashMap<String, ResourceInfo> resourceList = null;
   private static final Logger LOG = LoggerFactory.getLogger(GenericEntityCollectionProcessor.class);
   private static final int PAGE_SIZE = 100;

   /**
    * If you use this constructor, make sure to set your resourceInfo
    * 
    * @param connection
    */
   public GenericEntityCollectionProcessor(Connection connection, String dbType) {
      this.resourceList = new HashMap<>();
      this.connect = connection;
      this.dbType = dbType;
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
         ResourceInfo resource)
         throws ODataApplicationException {
      if (this.dbType.equals("mongodb")) {
         OrderByOption orderByOption = uriInfo.getOrderByOption();
         Bson sortCriteria = null;
         if (orderByOption != null) {
            List<OrderByItem> orderItemList = orderByOption.getOrders();
            for (OrderByItem orderByItem : orderItemList) {
               Expression expression = orderByItem.getExpression();
               if (expression instanceof Member) {
                  UriResource uriResource = ((Member) expression).getResourcePath().getUriResourceParts().get(0);
                  if (uriResource instanceof UriResourcePrimitiveProperty) {
                     String sortField = ((UriResourcePrimitiveProperty) uriResource).getProperty().getName();
                     Bson sortOrder = orderByItem.isDescending() ? com.mongodb.client.model.Sorts.descending(sortField)
                           : com.mongodb.client.model.Sorts.ascending(sortField);
                     sortCriteria = (sortCriteria == null) ? sortOrder
                           : com.mongodb.client.model.Sorts.orderBy(sortCriteria, sortOrder);
                  }
               }
            }
         }
         return getDataFromMongo(edmEntitySet, uriInfo, isCount, resource, sortCriteria);
      } else {
         return getDataFromSQL(edmEntitySet, uriInfo, isCount, resource);
      }
   }

   protected EntityCollection getDataFromMongo(EdmEntitySet edmEntitySet, UriInfo uriInfo, boolean isCount,
         ResourceInfo resource, Bson sortCriteria)
         throws ODataApplicationException {
      EntityCollection entCollection = new EntityCollection();
      try {
         FilterOption filter = uriInfo.getFilterOption();
         Bson mongoCriteria = null;
         if (filter != null) {
            String filterJson = filter.getExpression().accept(new MongoDBFilterExpressionVisitor(resource));
            mongoCriteria = BsonDocument.parse(filterJson);
         }

         int topNumber = Optional.ofNullable(uriInfo.getTopOption()).map(TopOption::getValue).orElse(PAGE_SIZE);
         int skipNumber = Optional.ofNullable(uriInfo.getSkipOption()).map(SkipOption::getValue).orElse(0);
         LOG.info("sortCriteria: " + sortCriteria);
         if (sortCriteria != null) {
            LOG.info("sortCriteria != null " + sortCriteria);
            return resource.executeMongoQuery(mongoCriteria, skipNumber, topNumber, sortCriteria);
         } else {
            return resource.executeMongoQuery(mongoCriteria, skipNumber, topNumber);
         }

      } catch (Exception e) {
         LOG.error("Server Error occurred in reading {}", resource.getResourceName(), e);
      }
      return entCollection;
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
            if (this.dbType.equals("mysql")) {
               sqlCriteria = filter.getExpression().accept(new MySQLFilterExpressionVisitor(resource));
            } else if (this.dbType.equals("postgres")) {
               sqlCriteria = filter.getExpression().accept(new PostgreSQLFilterExpressionVisitor(resource));
            } else {
               sqlCriteria = filter.getExpression().accept(new PostgreSQLFilterExpressionVisitor(resource));
            }
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
      try {
         String dbProductName = connect.getMetaData().getDatabaseProductName().toLowerCase();

         if (dbProductName.contains("mysql")) {
            LOG.info("mysql");
            return "mysql";
         } else if (dbProductName.contains("postgres")) {
            LOG.info("postgres");
            return "postgres";
         } else if (dbProductName.contains("mongodb")) {
            LOG.info("mongodb");
            return "mongodb";
         }
      } catch (SQLException e) {
         LOG.error("Error connection to the database", e);
      }
      return "unknown";
   }

}

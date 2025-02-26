package org.reso.service.data;

import com.google.gson.Gson;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.ReadPreference;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectItem;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.bson.Document;
import org.reso.service.data.common.CommonDataProcessing;
import org.reso.service.data.definition.FieldDefinition;
import org.reso.service.data.meta.EnumFieldInfo;
import org.reso.service.data.meta.FieldInfo;
import org.reso.service.data.meta.ResourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;

import static org.reso.service.servlet.RESOservlet.resourceLookup;

public class GenericEntityProcessor implements EntityProcessor {
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private final MongoClient mongoClient;
    private HashMap<String, ResourceInfo> resourceList = null;
    private static final Logger LOG = LoggerFactory.getLogger(GenericEntityCollectionProcessor.class);
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private static final int TIMEOUT = 5000; // 5 seconds timeout
    private static final int MONGO_TIMEOUT = 10000; // 10 seconds timeout for MongoDB operations

    public GenericEntityProcessor(MongoClient providedMongoClient) {
        Map<String, String> env = System.getenv();
        this.dbUrl = env.getOrDefault("JDBC_URL", "jdbc:mysql://mysql-db:3306/reso");
        this.dbUser = env.getOrDefault("DB_USERNAME", "root");
        this.dbPassword = env.getOrDefault("DB_PASSWORD", "root");
        this.resourceList = new HashMap<>();

        // Initialize MongoDB client with proper settings
        MongoClient initializedClient = providedMongoClient;
        if (providedMongoClient == null) {
            String mongoUri = env.getOrDefault("MONGO_CONNECTION_STR", "mongodb://localhost:27017");
            try {
                MongoClientSettings settings = MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(mongoUri))
                        .applyToSslSettings(builder -> builder.enabled(true)
                                .invalidHostNameAllowed(true))
                        .applyToClusterSettings(
                                builder -> builder.serverSelectionTimeout(MONGO_TIMEOUT, TimeUnit.MILLISECONDS)
                                        .localThreshold(1000, TimeUnit.MILLISECONDS)
                                        .mode(ClusterConnectionMode.MULTIPLE))
                        .applyToSocketSettings(builder -> builder.connectTimeout(MONGO_TIMEOUT, TimeUnit.MILLISECONDS)
                                .readTimeout(MONGO_TIMEOUT, TimeUnit.MILLISECONDS))
                        .applyToConnectionPoolSettings(
                                builder -> builder.maxWaitTime(MONGO_TIMEOUT, TimeUnit.MILLISECONDS)
                                        .maxConnectionIdleTime(30000, TimeUnit.MILLISECONDS)
                                        .maxSize(50)
                                        .minSize(1))
                        .retryWrites(true)
                        .retryReads(true)
                        .build();

                initializedClient = MongoClients.create(settings);
                LOG.info("MongoDB client initialized with URI: {}",
                        mongoUri.replaceAll(":[^/]+@", ":****@"));
            } catch (Exception e) {
                LOG.error("Failed to initialize MongoDB client: " + e.getMessage(), e);
                initializedClient = null;
            }
        }
        this.mongoClient = initializedClient;

        // Test database connections
        testDatabaseConnections();
    }

    private void testDatabaseConnections() {
        // Test MySQL connection
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            LOG.info("Successfully connected to MySQL database");
        } catch (SQLException e) {
            LOG.error("Failed to connect to MySQL database: " + e.getMessage(), e);
        }

        // Test MongoDB connection with proper error handling and retries
        if (mongoClient != null) {
            int retryCount = 3;
            int retryDelayMs = 1000;

            for (int i = 0; i < retryCount; i++) {
                try {
                    MongoDatabase adminDb = mongoClient.getDatabase("admin")
                            .withReadPreference(ReadPreference.nearest());
                    Document result = adminDb.runCommand(new Document("ping", 1))
                            .append("maxTimeMS", 5000);

                    if (result.getDouble("ok") == 1.0) {
                        LOG.info("Successfully connected to MongoDB on attempt {}", i + 1);
                        return;
                    } else {
                        LOG.error("MongoDB ping command failed on attempt {}", i + 1);
                    }
                } catch (MongoException e) {
                    LOG.error("Failed to connect to MongoDB on attempt {}: {}", i + 1, e.getMessage());
                    if (i < retryCount - 1) {
                        try {
                            Thread.sleep(retryDelayMs * (i + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            LOG.error("Failed to establish MongoDB connection after {} attempts", retryCount);
        }
    }

    private Connection getMySQLConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setNetworkTimeout(Executors.newSingleThreadExecutor(), TIMEOUT);
            return conn;
        } catch (ClassNotFoundException e) {
            LOG.error("MySQL JDBC Driver not found", e);
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }

    public void addResource(ResourceInfo resource, String name) {
        resourceList.put(name, resource);
    }

    @Override
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        // 1. Retrieve info from URI
        // 1.1. retrieve the info about the requested entity set
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();

        // 1.2. retrieve the requested (root) entity
        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();

        // 2. retrieve data from backend
        // 2.1. retrieve the entity data, for which the key was specified in the URI
        ResourceInfo resource = resourceList.get(edmEntitySet.getName());
        if (resource == null) {
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(),
                    Locale.ENGLISH);
        }

        Entity entity = getData(edmEntitySet, keyPredicates, resource, uriInfo);
        if (entity == null) {
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(),
                    Locale.ENGLISH);
        }

        // 3. serialize
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).suffix(ContextURL.Suffix.ENTITY).build();
        // expand and select currently not supported
        EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();

        ODataSerializer serializer = odata.createSerializer(responseFormat);
        SerializerException cachedException = null;
        try {
            SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, options);
            response.setContent(serializerResult.getContent());
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (SerializerException e) {
            cachedException = e;
        }

        if (cachedException != null) {
            throw cachedException;
        }
    }

    /**
     * Reads data from a resource and returns it as a HashMap
     * 
     * @param keyPredicates
     * @param resource
     * @return
     */
    private HashMap<String, Object> getDataToHash(List<UriParameter> keyPredicates, ResourceInfo resource) {
        return CommonDataProcessing.translateEntityToMap(this.getData(null, keyPredicates, resource, null));
    }

    protected Entity getData(EdmEntitySet edmEntitySet, List<UriParameter> keyPredicates, ResourceInfo resource,
            UriInfo uriInfo) {
        Entity product = null;
        String dbType = System.getenv().getOrDefault("DB_TYPE", "mongodb").toLowerCase();

        try {
            if (dbType.equals("mongodb")) {
                product = getDataFromMongo(resource, keyPredicates, uriInfo);
            } else {
                product = getDataFromSQL(resource, keyPredicates, uriInfo);
            }
        } catch (Exception e) {
            LOG.error("Server Error occurred in reading " + resource.getResourceName(), e);
        }

        return product;
    }

    private Entity getDataFromMongo(ResourceInfo resource, List<UriParameter> keyPredicates, UriInfo uriInfo) {
        if (mongoClient == null) {
            LOG.error("MongoDB client is not initialized");
            return null;
        }

        String primaryFieldName = resource.getPrimaryKeyName();
        List<FieldInfo> enumFields = CommonDataProcessing.gatherEnumFields(resource);
        HashMap<String, Object> enumValues = new HashMap<>();
        Entity product = null;

        Document query = new Document();
        if (keyPredicates != null) {
            for (UriParameter key : keyPredicates) {
                String value = key.getText();
                if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                query.append(key.getName(), value);
            }
        }

        try {
            MongoDatabase database = mongoClient.getDatabase("reso");
            MongoCollection<Document> collection = database.getCollection(resource.getTableName());

            // Add a timeout to the find operation
            Document doc = collection.find(query)
                    .maxTime(5000, TimeUnit.MILLISECONDS)
                    .first();

            if (doc != null) {
                product = CommonDataProcessing.getEntityFromDocument(doc, resource);
                String resourceRecordKey = doc.getString(primaryFieldName);

                if (!enumFields.isEmpty()) {
                    Document lookupQuery = new Document("ResourceRecordKey", resourceRecordKey);
                    try (MongoCursor<Document> lookupCursor = mongoClient.getDatabase("reso")
                            .getCollection("lookup_value")
                            .find(lookupQuery)
                            .maxTime(5000, TimeUnit.MILLISECONDS)
                            .iterator()) {
                        HashMap<String, HashMap<String, Object>> entities = new HashMap<>();
                        entities.put(resourceRecordKey, enumValues);

                        while (lookupCursor.hasNext()) {
                            Document lookupDoc = lookupCursor.next();
                            CommonDataProcessing.getEntityValues(lookupDoc, entities, enumFields);
                        }
                        CommonDataProcessing.setEntityEnums(enumValues, product, enumFields);
                    }
                }

                // Handle $expand for MongoDB
                if (uriInfo != null && uriInfo.getExpandOption() != null) {
                    handleMongoExpand(product, resource, resourceRecordKey, uriInfo.getExpandOption());
                }
            }
        } catch (Exception e) {
            LOG.error("Error querying MongoDB: " + e.getMessage(), e);
        }

        return product;
    }

    private void handleMongoExpand(Entity sourceEntity, ResourceInfo sourceResource, String sourceKey,
            ExpandOption expandOption) {
        if (mongoClient == null) {
            LOG.error("MongoDB client is not initialized");
            return;
        }

        for (ExpandItem expandItem : expandOption.getExpandItems()) {
            UriResource expandPath = expandItem.getResourcePath().getUriResourceParts().get(0);
            if (expandPath instanceof UriResourceNavigation) {
                UriResourceNavigation expandNavigation = (UriResourceNavigation) expandPath;
                EdmNavigationProperty edmNavigationProperty = expandNavigation.getProperty();
                String expandResourceName = edmNavigationProperty.getType().getName();
                ResourceInfo expandResource = resourceLookup.get(expandResourceName);

                if (expandResource != null) {
                    try {
                        Document query = new Document();
                        if (edmNavigationProperty.isCollection()) {
                            switch (expandResource.getResourceName()) {
                                case "Media":
                                case "Queue":
                                case "OtherPhone":
                                case "SocialMedia":
                                case "HistoryTransactional":
                                    query.append("ResourceName", sourceResource.getResourceName())
                                            .append("ResourceRecordKey", sourceKey);
                                    break;
                                default:
                                    query.append(sourceResource.getPrimaryKeyName(), sourceKey);
                            }
                        } else {
                            Property expandResourceKey = sourceEntity
                                    .getProperty(edmNavigationProperty.getName() + "Key");
                            if (expandResourceKey != null && expandResourceKey.getValue() != null) {
                                query.append(expandResource.getPrimaryKeyName(),
                                        expandResourceKey.getValue().toString());
                            }
                        }

                        EntityCollection expandEntities = new EntityCollection();
                        try (MongoCursor<Document> cursor = mongoClient.getDatabase("reso")
                                .getCollection(expandResource.getTableName())
                                .find(query)
                                .maxTime(5000, TimeUnit.MILLISECONDS)
                                .iterator()) {
                            while (cursor.hasNext()) {
                                Document doc = cursor.next();
                                Entity expandEntity = CommonDataProcessing.getEntityFromDocument(doc, expandResource);
                                expandEntities.getEntities().add(expandEntity);
                            }
                        }

                        Link link = new Link();
                        link.setTitle(edmNavigationProperty.getName());
                        link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
                        link.setInlineEntitySet(expandEntities);
                        sourceEntity.getNavigationLinks().add(link);
                    } catch (Exception e) {
                        LOG.error("Error handling MongoDB expand for " + expandResourceName + ": " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private Entity getDataFromSQL(ResourceInfo resource, List<UriParameter> keyPredicates, UriInfo uriInfo) {
        Entity product = null;
        try (Connection connection = getMySQLConnection()) {
            String primaryFieldName = resource.getPrimaryKeyName();
            List<FieldInfo> enumFields = CommonDataProcessing.gatherEnumFields(resource);
            HashMap<String, Object> enumValues = new HashMap<>();

            StringBuilder queryBuilder = new StringBuilder("SELECT * FROM ")
                    .append(resource.getTableName())
                    .append(" WHERE ");

            if (keyPredicates != null) {
                for (UriParameter key : keyPredicates) {
                    queryBuilder.append(key.getName())
                            .append(" = ")
                            .append(key.getText())
                            .append(" AND ");
                }
                queryBuilder.setLength(queryBuilder.length() - 5); // Remove last " AND "
            }

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(queryBuilder.toString())) {

                if (resultSet.next()) {
                    product = CommonDataProcessing.getEntityFromRow(resultSet, resource, null);
                    String resourceRecordKey = resultSet.getString(primaryFieldName);

                    if (!enumFields.isEmpty()) {
                        String enumQuery = "SELECT * FROM lookup_value WHERE ResourceRecordKey = '" + resourceRecordKey
                                + "'";
                        try (Statement enumStatement = connection.createStatement();
                                ResultSet enumResultSet = enumStatement.executeQuery(enumQuery)) {

                            HashMap<String, HashMap<String, Object>> entities = new HashMap<>();
                            entities.put(resourceRecordKey, enumValues);

                            while (enumResultSet.next()) {
                                CommonDataProcessing.getEntityValues(enumResultSet, entities, enumFields);
                            }
                            CommonDataProcessing.setEntityEnums(enumValues, product, enumFields);
                        }
                    }

                    // Handle $expand for SQL
                    if (uriInfo != null && uriInfo.getExpandOption() != null) {
                        for (ExpandItem expandItem : uriInfo.getExpandOption().getExpandItems()) {
                            UriResource expandPath = expandItem.getResourcePath().getUriResourceParts().get(0);
                            if (expandPath instanceof UriResourceNavigation) {
                                UriResourceNavigation expandNavigation = (UriResourceNavigation) expandPath;
                                EdmNavigationProperty edmNavigationProperty = expandNavigation.getProperty();
                                EntityCollection expandEntities = CommonDataProcessing.getExpandEntityCollection(
                                        connection, edmNavigationProperty, product, resource, resourceRecordKey);

                                Link link = new Link();
                                link.setTitle(edmNavigationProperty.getName());
                                link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
                                link.setInlineEntitySet(expandEntities);
                                product.getNavigationLinks().add(link);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error querying SQL database: " + e.getMessage(), e);
        }
        return product;
    }

    private URI createId(String entitySetName, Object id) {
        try {
            return new URI(entitySetName + "('" + id + "')");
        } catch (URISyntaxException e) {
            throw new ODataRuntimeException("Unable to create id for entity: " + entitySetName, e);
        }
    }

    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
            ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Create not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    private void saveData(ResourceInfo resource, HashMap<String, Object> mappedObj) {
        String queryString = "insert into " + resource.getTableName();
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); // For SQL DATE

            com.mongodb.client.MongoCursor<org.bson.Document> cursor = mongoClient.getDatabase("reso")
                    .getCollection(resource.getTableName()).find()
                    .iterator();
            ArrayList<String> columnNames = new ArrayList<>();
            ArrayList<String> columnValues = new ArrayList<>();

            ArrayList<FieldInfo> fieldList = resource.getFieldList();
            HashMap<String, FieldInfo> fieldLookup = new HashMap<>();

            for (FieldInfo field : fieldList) {
                fieldLookup.put(field.getFieldName(), field);
            }

            for (Map.Entry<String, Object> entrySet : mappedObj.entrySet()) {
                Gson gson = new Gson();
                String key = entrySet.getKey();
                Object value = entrySet.getValue();
                columnNames.add(key);

                FieldInfo field = fieldLookup.get(key);

                if (value == null) {
                    columnValues.add("NULL");
                } else if (field.getType().equals(EdmPrimitiveTypeKind.String.getFullQualifiedName())) {
                    boolean isList = value instanceof ArrayList;
                    columnValues.add("'" + (isList ? gson.toJson(value) : value.toString()) + "'");
                } else if (field.getType().equals(EdmPrimitiveTypeKind.DateTimeOffset.getFullQualifiedName())) {
                    columnValues.add("'" + value.toString() + "'");
                } else if (field.getType().equals(EdmPrimitiveTypeKind.Date.getFullQualifiedName())) {
                    if (value instanceof GregorianCalendar) {
                        String formattedDate = dateFormat.format(((GregorianCalendar) value).getTime());
                        columnValues.add("'" + formattedDate + "'");
                    } else {
                        columnValues.add("'" + value.toString() + "'");
                    }
                } else {
                    columnValues.add(value.toString());
                }

            }

            queryString = queryString + " (" + String.join(",", columnNames) + ") values ("
                    + String.join(",", columnValues) + ")";

            try (Connection connection = getMySQLConnection();
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate(queryString);
            }
        } catch (SQLException e) {
            LOG.error("Error executing SQL query: " + e.getMessage());
        }
    }

    private void saveDataMongo(ResourceInfo resource, HashMap<String, Object> mappedObj) {
        Map<String, String> env = System.getenv();
        String syncConnStr = env.get("MONGO_SYNC_CONNECTION_STR");

        try (MongoClient mongoClient = MongoClients.create(syncConnStr)) {
            MongoDatabase database = mongoClient.getDatabase("reso");
            MongoCollection<Document> collection = database.getCollection(resource.getTableName());

            Document document = new Document();

            ArrayList<FieldInfo> fieldList = resource.getFieldList();
            HashMap<String, FieldInfo> fieldLookup = new HashMap<>();

            for (FieldInfo field : fieldList) {
                fieldLookup.put(field.getFieldName(), field);
            }

            for (Map.Entry<String, Object> entry : mappedObj.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                FieldInfo field = fieldLookup.get(key);
                FullQualifiedName fieldType = field.getType();
                if (value != null) {
                    if (fieldType.equals(EdmPrimitiveTypeKind.String.getFullQualifiedName())) {
                        document.append(key, value);
                    } else if (fieldType.equals(EdmPrimitiveTypeKind.DateTimeOffset.getFullQualifiedName())) {
                        // Assuming the date is in ISO format or needs to be converted to a Date object
                        try {
                            document.append(key,
                                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(value.toString()));
                        } catch (ParseException e) {
                            LOG.error("Date parsing error", e);
                        }
                    } else {
                        document.append(key, value);
                    }
                } else {
                    document.append(key, null);
                }
            }
            collection.insertOne(document);
        }
    }

    private void saveEnumData(ResourceInfo resource, HashMap<String, Object> enumValues, String resourceRecordKey) {
        String dbType = System.getenv().getOrDefault("DB_TYPE", "mongodb").toLowerCase();
        for (String key : enumValues.keySet()) {
            Object value = enumValues.get(key);
            if (dbType.equals("mongodb")) {
                saveEnumDataMongo(resource, key, value, resourceRecordKey);
            } else {
                saveEnumDataSQL(resource, key, value, resourceRecordKey);
            }
        }
    }

    private void saveEnumDataSQL(ResourceInfo resource, String lookupEnumField, Object values,
            String resourceRecordKey) {
        String queryString = "INSERT INTO lookup_value (FieldName, LookupKey, ResourceName, ResourceRecordKey) VALUES (?, ?, ?, ?)";

        try (Connection connection = getMySQLConnection();
                PreparedStatement statement = connection.prepareStatement(queryString)) {
            statement.setString(1, lookupEnumField);
            statement.setString(2, values.toString());
            statement.setString(3, resource.getResourcesName());
            statement.setString(4, resourceRecordKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error inserting data into SQL database", e);
        }
    }

    private void saveEnumDataMongo(ResourceInfo resource, String lookupEnumField, Object values,
            String resourceRecordKey) {
        try {
            mongoClient.getDatabase("reso").getCollection("lookup_value").insertOne(new Document()
                    .append("FieldName", lookupEnumField)
                    .append("LookupKey", values)
                    .append("ResourceName", resource.getResourcesName())
                    .append("ResourceRecordKey", resourceRecordKey));
        } catch (Exception e) {
            LOG.error("Error inserting data into MongoDB", e);
        }
    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
            ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Update not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Delete not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }
}

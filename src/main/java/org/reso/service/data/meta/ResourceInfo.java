package org.reso.service.data.meta;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.jdbc.MongoConnection;
import com.mongodb.jdbc.MongoDatabaseMetaData;
import com.mongodb.jdbc.MongoResultSet;
import org.bson.Document;
import com.mongodb.client.MongoIterable;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.reso.service.data.common.CommonDataProcessing;
import org.reso.service.servlet.RESOservlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResourceInfo {
    protected String tableName;
    protected String resourceName;
    protected String resourcesName;
    protected FullQualifiedName fqn;
    protected String primaryKeyName;

    protected static final Logger LOG = LoggerFactory.getLogger(ResourceInfo.class);

    /**
     * Accessors
     */

    public String getTableName() {
        return tableName;
    }

    public String getResourcesName() {
        return resourcesName;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getPrimaryKeyName() {
        return primaryKeyName;
    }

    public ArrayList<FieldInfo> getFieldList() {
        return null;
    }

    public Boolean useCustomDatasource() {
        return false;
    }

    public FullQualifiedName getFqn(String namespace) {
        if (this.fqn == null)
            this.fqn = new FullQualifiedName(namespace, getResourceName());

        return this.fqn;
    }

    public void findPrimaryKey(Connection connect) throws SQLException {
        String primaryKey = null;
        DatabaseMetaData metadata = connect.getMetaData();
        ResultSet pkColumns = metadata.getPrimaryKeys(null, null, getTableName());

        while (pkColumns.next()) {
            Integer pkPosition = pkColumns.getInt("KEY_SEQ");
            String pkColumnName = pkColumns.getString("COLUMN_NAME");
            LOG.debug("" + pkColumnName + " is the " + pkPosition + ". column of the primary key of the table " + tableName);
            primaryKey = pkColumnName; //.toLowerCase();  // lowercase only needed for PostgreSQL
        }

        String[] splitKey = primaryKey.split("Numeric");
        if (splitKey.length >= 1)
            primaryKey = splitKey[0];

        ArrayList<FieldInfo> fields = this.getFieldList();
        for (FieldInfo field : fields) {
            String fieldName = field.getFieldName();
            if (primaryKey.equals(fieldName))
                primaryKey = field.getODATAFieldName();
        }

        this.primaryKeyName = primaryKey;
    }

    public void findMongoPrimaryKey(MongoClient mongoClient) {
        String primaryKey = null;

        // Access database and collection
        MongoDatabase database = mongoClient.getDatabase("reso");
        MongoCollection<Document> collection = database.getCollection(tableName);

//      Uncomment to query Lookup endpoint
//      MongoDatabase database = mongoClient.getDatabase("reso");
//      MongoCollection<Document> collection = database.getCollection("Property");
        ArrayList<Document> indexDocs = collection.listIndexes().into(new ArrayList<Document>());

        // List indexes and iterate over them
        for (Document indexDoc : indexDocs) {
            LOG.info("Index Document: " + indexDoc.toJson());

            // Check if the index is unique
            Boolean isUnique = indexDoc.getBoolean("unique", false);

            // Get the indexed field(s)
            Document keyDoc = (Document) indexDoc.get("key");
            if (keyDoc != null && isUnique) {
                for (String indexedField : keyDoc.keySet()) {
                    primaryKey = indexedField; // Get the first unique indexed field
                    LOG.info("Unique Index Found: Field = " + primaryKey);
                    break; // Exit loop after finding the first unique index
                }
            }

            if (primaryKey != null) {
                break; // Exit outer loop once a unique index is found
            }
        }

        if (primaryKey == null) {
            LOG.warn("No unique index found for collection: " + tableName);
        }

        this.primaryKeyName = primaryKey;
    }


    public Entity getData(EdmEntitySet edmEntitySet, List<UriParameter> keyPredicates) {
        return null;
    }

    public EntityCollection getData(EdmEntitySet edmEntitySet, UriInfo uriInfo, boolean isCount) throws ODataApplicationException {
        return null;
    }
}

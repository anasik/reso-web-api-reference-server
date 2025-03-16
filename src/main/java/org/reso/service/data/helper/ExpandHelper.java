package org.reso.service.data.helper;

import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.bson.Document;
import org.reso.service.data.common.CommonDataProcessing;
import org.reso.service.data.meta.ResourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mongodb.client.MongoClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.reso.service.servlet.RESOservlet.resourceLookup;

public class ExpandHelper {
   private MongoClient mongoClient;
   private static final Logger LOG = LoggerFactory.getLogger(ExpandHelper.class);
   private static final Map<String, NavigationConfig> NAVIGATION_CONFIGS = new HashMap<>();

   public ExpandHelper(MongoClient mongoClient) {
      this.mongoClient = mongoClient;
   }

   private enum ResourceType {
      PROPERTY("Property"),
      MEMBER("Member"),
      OFFICE("Office"),
      CONTACTS("Contacts"),
      TEAMS("Teams"),
      SHOWING("Showing");

      private final String value;

      ResourceType(String value) {
         this.value = value;
      }

      public String getValue() {
         return value;
      }

   }

   private enum CollectionType {
      MEMBER("member", "Member"),
      OFFICE("office", "Office"),
      MEDIA("media", "Media"),
      SOCIAL_MEDIA("socialmedia", "SocialMedia"),
      HISTORY_TRANSACTIONAL("historytransactional", "HistoryTransactional"),
      GREEN_VERIFICATION("propertygreenverification", "GreenBuildingVerification"),
      ROOMS("propertyrooms", "Rooms"),
      UNIT_TYPES("propertyunittypes", "UnitTypes"),
      POWER_PRODUCTION("propertypowerproduction", "PowerProduction"),
      OPEN_HOUSE("openhouse", "OpenHouse"),
      TEAMS("teams", "Teams"),
      OUID("ouid", "OUID"),
      PROPERTY("property", "Property"),
      OTHER_PHONE("other_phone", "OtherPhone");

      private final String collectionName;
      private final String resourceName;

      CollectionType(String collectionName, String resourceName) {
         this.collectionName = collectionName;
         this.resourceName = resourceName;
      }

      public String getCollectionName() {
         return collectionName;
      }

      public String getResourceName() {
         return resourceName;
      }
   }

   private static class NavigationBuilder {
      private final ResourceType sourceResource;
      private final String navProperty;
      private final CollectionType targetCollection;
      private String sourceKey;
      private String targetKey;
      private boolean isCollection;

      private NavigationBuilder(ResourceType sourceResource, String navProperty, CollectionType targetCollection) {
         this.sourceResource = sourceResource;
         this.navProperty = navProperty;
         this.targetCollection = targetCollection;
      }

      public static NavigationBuilder from(ResourceType sourceResource, String navProperty,
            CollectionType targetCollection) {
         return new NavigationBuilder(sourceResource, navProperty, targetCollection);
      }

      public NavigationBuilder withKeys(String sourceKey, String targetKey) {
         this.sourceKey = sourceKey;
         this.targetKey = targetKey;
         return this;
      }

      public NavigationBuilder asCollection(boolean isCollection) {
         this.isCollection = isCollection;
         return this;
      }

      public void add() {
         addNavConfig(
               sourceResource.getValue(),
               navProperty,
               targetCollection.getCollectionName(),
               sourceKey,
               targetKey,
               isCollection);
      }

   }

   private static class ResourceMapping {
      private static final Map<String, String> COLLECTION_TO_RESOURCE = new HashMap<>();

      static {
         for (CollectionType type : CollectionType.values()) {
            COLLECTION_TO_RESOURCE.put(type.getCollectionName(), type.getResourceName());
         }
      }

      static String getResourceName(String collection, String navPropertyName) {
         return COLLECTION_TO_RESOURCE.getOrDefault(collection, navPropertyName);
      }
   }

   static {
      initializePropertyNavigations();
      initializeMemberNavigations();
      initializeOfficeNavigations();
      initializeContactNavigations();
      initializeTeamNavigations();
      initializeShowingNavigations();
   }

   private static void initializePropertyNavigations() {
      // Agent related navigations
      NavigationBuilder.from(ResourceType.PROPERTY, "BuyerAgent", CollectionType.MEMBER)
            .withKeys("BuyerAgentKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "CoBuyerAgent", CollectionType.MEMBER)
            .withKeys("CoBuyerAgentKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "CoListAgent", CollectionType.MEMBER)
            .withKeys("CoListAgentKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "ListAgent", CollectionType.MEMBER)
            .withKeys("ListAgentKey", "MemberKey")
            .asCollection(false)
            .add();

      // Office related navigations
      NavigationBuilder.from(ResourceType.PROPERTY, "BuyerOffice", CollectionType.OFFICE)
            .withKeys("BuyerOfficeKey", "OfficeKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "CoBuyerOffice", CollectionType.OFFICE)
            .withKeys("CoBuyerOfficeKey", "OfficeKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "CoListOffice", CollectionType.OFFICE)
            .withKeys("CoListOfficeKey", "OfficeKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "ListOffice", CollectionType.OFFICE)
            .withKeys("ListOfficeKey", "OfficeKey")
            .asCollection(false)
            .add();

      // Team related navigations
      NavigationBuilder.from(ResourceType.PROPERTY, "BuyerTeam", CollectionType.TEAMS)
            .withKeys("BuyerTeamKey", "TeamKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "ListTeam", CollectionType.TEAMS)
            .withKeys("ListTeamKey", "TeamKey")
            .asCollection(false)
            .add();

      // Resource record based navigations
      String resourceRecordKey = "ResourceRecordKey,ResourceName";
      NavigationBuilder.from(ResourceType.PROPERTY, "Media", CollectionType.MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "SocialMedia", CollectionType.SOCIAL_MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "HistoryTransactional", CollectionType.HISTORY_TRANSACTIONAL)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      // ListingKey based navigations
      String listingKey = "ListingKey";
      NavigationBuilder.from(ResourceType.PROPERTY, "GreenBuildingVerification", CollectionType.GREEN_VERIFICATION)
            .withKeys(listingKey, listingKey)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "OpenHouse", CollectionType.OPEN_HOUSE)
            .withKeys(listingKey, listingKey)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "PowerProduction", CollectionType.POWER_PRODUCTION)
            .withKeys(listingKey, listingKey)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "Rooms", CollectionType.ROOMS)
            .withKeys(listingKey, listingKey)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "UnitTypes", CollectionType.UNIT_TYPES)
            .withKeys(listingKey, listingKey)
            .asCollection(true)
            .add();

      // OUID navigations
      NavigationBuilder.from(ResourceType.PROPERTY, "OriginatingSystem", CollectionType.OUID)
            .withKeys("OriginatingSystemID", "OrganizationUniqueId")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.PROPERTY, "SourceSystem", CollectionType.OUID)
            .withKeys("SourceSystemID", "OrganizationUniqueId")
            .asCollection(false)
            .add();
   }

   private static void initializeMemberNavigations() {
      String resourceRecordKey = "ResourceRecordKey,ResourceName";

      NavigationBuilder.from(ResourceType.MEMBER, "Office", CollectionType.OFFICE)
            .withKeys("OfficeKey", "OfficeKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.MEMBER, "Media", CollectionType.MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.MEMBER, "MemberSocialMedia", CollectionType.SOCIAL_MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.MEMBER, "HistoryTransactional", CollectionType.HISTORY_TRANSACTIONAL)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.MEMBER, "Listings", CollectionType.PROPERTY)
            .withKeys("MemberKey", "ListAgentKey")
            .asCollection(true)
            .add();
   }

   private static void initializeOfficeNavigations() {
      NavigationBuilder.from(ResourceType.OFFICE, "MainOffice", CollectionType.OFFICE)
            .withKeys("MainOfficeKey", "OfficeKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.OFFICE, "OfficeBroker", CollectionType.MEMBER)
            .withKeys("OfficeBrokerKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.OFFICE, "OfficeManager", CollectionType.MEMBER)
            .withKeys("OfficeManagerKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.OFFICE, "Listings", CollectionType.PROPERTY)
            .withKeys("OfficeKey", "ListOfficeKey")
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.OFFICE, "Agents", CollectionType.MEMBER)
            .withKeys("OfficeKey", "OfficeKey")
            .asCollection(true)
            .add();
   }

   private static void initializeContactNavigations() {
      String resourceRecordKey = "ResourceRecordKey,ResourceName";

      NavigationBuilder.from(ResourceType.CONTACTS, "ContactsOtherPhone", CollectionType.OTHER_PHONE)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.CONTACTS, "ContactsSocialMedia", CollectionType.SOCIAL_MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.CONTACTS, "HistoryTransactional", CollectionType.HISTORY_TRANSACTIONAL)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.CONTACTS, "Media", CollectionType.MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.CONTACTS, "OwnerMember", CollectionType.MEMBER)
            .withKeys("OwnerMemberKey", "MemberKey")
            .asCollection(false)
            .add();
   }

   private static void initializeTeamNavigations() {
      String resourceRecordKey = "ResourceRecordKey,ResourceName";

      NavigationBuilder.from(ResourceType.TEAMS, "TeamLead", CollectionType.MEMBER)
            .withKeys("TeamLeadKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.TEAMS, "Media", CollectionType.MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.TEAMS, "TeamsSocialMedia", CollectionType.SOCIAL_MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.TEAMS, "HistoryTransactional", CollectionType.HISTORY_TRANSACTIONAL)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();
   }

   private static void initializeShowingNavigations() {
      String resourceRecordKey = "ResourceRecordKey,ResourceName";

      NavigationBuilder.from(ResourceType.SHOWING, "ShowingAgent", CollectionType.MEMBER)
            .withKeys("ShowingAgentKey", "MemberKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.SHOWING, "Listing", CollectionType.PROPERTY)
            .withKeys("ListingKey", "ListingKey")
            .asCollection(false)
            .add();

      NavigationBuilder.from(ResourceType.SHOWING, "Media", CollectionType.MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();

      NavigationBuilder.from(ResourceType.SHOWING, "SocialMedia", CollectionType.SOCIAL_MEDIA)
            .withKeys(resourceRecordKey, null)
            .asCollection(true)
            .add();
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

   public void handleMongoExpand(EntityCollection sourceCollection, ResourceInfo sourceResource,
         ExpandOption expandOption) {

      if (mongoClient == null) {
         LOG.error("MongoDB client is not initialized");
         return;
      }

      try {
         MongoDatabase database = mongoClient.getDatabase("reso");

         for (Entity sourceEntity : sourceCollection.getEntities()) {
            for (ExpandItem expandItem : expandOption.getExpandItems()) {
               handleExpandItem(database, sourceEntity, sourceResource, expandItem);
            }
         }
      } catch (Exception e) {
         LOG.error("Error in handleMongoExpand: {}", e.getMessage(), e);
      }
   }

   private void handleExpandItem(MongoDatabase database, Entity sourceEntity, ResourceInfo sourceResource,
         ExpandItem expandItem) {
      UriResource expandPath = expandItem.getResourcePath().getUriResourceParts().get(0);
      if (!(expandPath instanceof UriResourceNavigation)) {
         return;
      }

      UriResourceNavigation expandNavigation = (UriResourceNavigation) expandPath;
      String navPropertyName = expandNavigation.getProperty().getName();
      String configKey = sourceResource.getResourceName() + "." + navPropertyName;

      NavigationConfig config = NAVIGATION_CONFIGS.get(configKey);
      if (config == null) {
         LOG.warn("Unsupported navigation property: {}", navPropertyName);
         return;
      }

      Document query = buildNavigationQuery(sourceEntity, sourceResource, config);
      if (query.isEmpty()) {
         LOG.warn("No key found for navigation property: {}", navPropertyName);
         return;
      }

      EntityCollection expandEntities = executeNavigationQuery(database, config, query, navPropertyName);
      addNavigationLink(sourceEntity, navPropertyName, expandEntities, config.isCollection);
   }

   private Document buildNavigationQuery(Entity sourceEntity, ResourceInfo sourceResource, NavigationConfig config) {
      Document query = new Document();

      if (config.sourceKey.contains(",")) {
         handleCompositeKey(query, sourceEntity, sourceResource);
      } else {
         handleSingleKey(query, sourceEntity, config);
      }

      return query;
   }

   private void handleCompositeKey(Document query, Entity sourceEntity, ResourceInfo sourceResource) {
      Property keyProp = sourceEntity.getProperty(sourceResource.getPrimaryKeyName());
      if (keyProp != null && keyProp.getValue() != null) {
         query.append("ResourceRecordKey", keyProp.getValue().toString());
         query.append("ResourceName", sourceResource.getResourceName());
      }
   }

   private void handleSingleKey(Document query, Entity sourceEntity, NavigationConfig config) {
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

   private EntityCollection executeNavigationQuery(MongoDatabase database, NavigationConfig config,
         Document query, String navPropertyName) {
      LOG.info("Querying {} with filter: {}", config.targetCollection, query.toJson());
      MongoCollection<Document> collection = database.getCollection(config.targetCollection);
      EntityCollection expandEntities = new EntityCollection();

      try (MongoCursor<Document> cursor = collection.find(query).maxTime(5000, TimeUnit.MILLISECONDS).iterator()) {
         while (cursor.hasNext()) {
            Document doc = cursor.next();
            LOG.info("Found {} document: {}", navPropertyName, doc.toJson());

            String resourceName = ResourceMapping.getResourceName(config.targetCollection, navPropertyName);
            ResourceInfo expandResource = resourceLookup.get(resourceName);

            if (expandResource == null) {
               LOG.error("Resource not found for expansion: {} (looking up as {})", navPropertyName, resourceName);
               continue;
            }

            Entity expandEntity = CommonDataProcessing.getEntityFromDocument(doc, expandResource);
            expandEntities.getEntities().add(expandEntity);
         }
      }

      return expandEntities;
   }

   private void addNavigationLink(Entity sourceEntity, String navPropertyName,
         EntityCollection expandEntities, boolean isCollection) {
      Link link = new Link();
      link.setTitle(navPropertyName);
      link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);

      if (!expandEntities.getEntities().isEmpty()) {
         if (isCollection) {
            link.setInlineEntitySet(expandEntities);
            LOG.info("Added {} {} items to entity", expandEntities.getEntities().size(), navPropertyName);
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

package org.reso.service.data.meta.builder;

import com.google.gson.stream.JsonReader;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.reso.service.data.meta.*;
import org.reso.service.tenant.TenantConfig;
import org.reso.service.tenant.TenantContext;
import org.reso.service.tenant.TenantConfigurationService;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefinitionBuilder {
   // Constants

   private static final String NAMESPACE = "org.reso.metadata";
   private static final String EDM_ENUM = NAMESPACE + ".enums";

   private static final Map<String, FullQualifiedName> EDM_MAP = Stream.of(
         new AbstractMap.SimpleEntry<>("Edm.String", EdmPrimitiveTypeKind.String.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.Boolean", EdmPrimitiveTypeKind.Boolean.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.Decimal", EdmPrimitiveTypeKind.Decimal.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.Double", EdmPrimitiveTypeKind.Double.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.Int32", EdmPrimitiveTypeKind.Int32.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.Int64", EdmPrimitiveTypeKind.Int64.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.Date", EdmPrimitiveTypeKind.Date.getFullQualifiedName()),
         new AbstractMap.SimpleEntry<>("Edm.DateTimeOffset",
               EdmPrimitiveTypeKind.DateTimeOffset.getFullQualifiedName()))
         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

   private static final Map<String, Boolean> HEADER_FIELDS = Stream.of(
         new AbstractMap.SimpleEntry<>("description", true),
         new AbstractMap.SimpleEntry<>("generatedOn", true),
         new AbstractMap.SimpleEntry<>("version", true))
         .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

   private static final Logger LOG = LoggerFactory.getLogger(DefinitionBuilder.class);
   
   // Remove static LOOKUP_TYPE and replace with instance variable
   private final String lookupType;

   // Internals
   private final String fileName;
   private JsonReader reader;

   // Constructor
   public DefinitionBuilder(String fileName) {
      this.fileName = fileName;
      
      // Get the lookup type from the tenant configuration
      String tenantId = TenantContext.getCurrentTenant();
      TenantConfig config = TenantConfigurationService.getTenantConfig(tenantId);
      this.lookupType = config.getLookupType();
      
      LOG.info("Creating DefinitionBuilder for tenant {}, using lookup type: {}", tenantId, this.lookupType);
      
      this.openFile();
   }
   
   // Constructor with explicit lookupType (for testing or special cases)
   public DefinitionBuilder(String fileName, String lookupType) {
      this.fileName = fileName;
      this.lookupType = lookupType;
      this.openFile();
   }

   public void openFile() {
      try {
         reader = new JsonReader(new FileReader("webapps/" + fileName));
      } catch (FileNotFoundException e) {
         LOG.info("ERROR:", e.getMessage());
         e.printStackTrace();
      }
   }

   private FieldObject readField() {
      return new FieldObject(reader);
   }

   private LookupObject readLookup() {
      return new LookupObject(reader);
   }

   private HashMap<String, ArrayList<GenericGSONobject>> createHashFromKey(ArrayList<GenericGSONobject> allObjects,
         String keyName) {
      HashMap<String, ArrayList<GenericGSONobject>> lookup = new HashMap<>();

      for (GenericGSONobject obj : allObjects) {
         Object keyValueObj = obj.getProperty(keyName);

         if (keyValueObj == null) {
            LOG.error("Null value found for key: " + keyName);
            continue; //
         }

         String keyValue = keyValueObj.toString(); // toString()

         ArrayList<GenericGSONobject> commonList = lookup.get(keyValue);
         if (commonList == null) {
            commonList = new ArrayList<>();
            lookup.put(keyValue, commonList);
         }

         commonList.add(obj);
      }

      return lookup;
   }

   // Function to convert camel case
   // string to snake case string
   public static String camelToSnake(String str) {

      // Empty String
      String result = "";

      // Append first character(in lower case)
      // to result string
      char c = str.charAt(0);
      result = result + Character.toLowerCase(c);

      // Traverse the string from
      // ist index to last index
      for (int i = 1; i < str.length(); i++) {

         char ch = str.charAt(i);

         // Check if the character is upper case
         // then append '_' and such character
         // (in lower case) to result string
         if (Character.isUpperCase(ch)) {
            result = result + '_';
            result = result
                  + Character.toLowerCase(ch);
         }

         // If the character is lower case then
         // add such character into result string
         else {
            result = result + ch;
         }
      }

      // return the result
      return result;
   }

   private List<ResourceInfo> createResources(ArrayList<GenericGSONobject> fields,
         ArrayList<GenericGSONobject> lookups) {
      HashMap<String, ArrayList<GenericGSONobject>> lookupMap = createHashFromKey(lookups, "lookupName");
      HashMap<String, ArrayList<GenericGSONobject>> fieldMap = createHashFromKey(fields, "resourceName");

      List<ResourceInfo> resources = new ArrayList<>();

      for (String resourceName : fieldMap.keySet()) {
         ArrayList<GenericGSONobject> resourceFields = fieldMap.get(resourceName);

         String tableName = resourceName.toLowerCase();

         if (!"ouid".equals(tableName)) {
            tableName = camelToSnake(resourceName); // @ToDo: This is NOT Guaranteed for all users
         }

         ResourceInfo resource = new GenericResourceInfo(resourceName, tableName);
         resources.add(resource);

         ArrayList<FieldInfo> fieldList = resource.getFieldList();

         for (GenericGSONobject field : resourceFields) {

            FieldInfo newField = null;

            String fieldName = (String) field.getProperty("fieldName");
            String fieldType = (String) field.getProperty("type");
            String fieldTypeName = (String) field.getProperty("typeName");
            Boolean nullable = (Boolean) field.getProperty("nullable");
            boolean isFlags = (Boolean.TRUE.equals(field.getProperty("isFlags")));
            boolean isCollection = (Boolean.TRUE.equals(field.getProperty("isCollection")));
            boolean isExpansion = (Boolean.TRUE.equals(field.getProperty("isExpansion")));

            Integer maxLength = (Integer) field.getProperty("maxLength");
            Integer scale = (Integer) field.getProperty("scale");
            Integer precision = (Integer) field.getProperty("precision");

            FullQualifiedName fqn = EDM_MAP.get(fieldType);
            if (fqn != null) {
               if (fqn.equals(EdmPrimitiveTypeKind.Int64.getFullQualifiedName())) {

                  Object rawValue = field.getProperty("value");
                  Long longValue = (rawValue != null) ? Long.parseLong(rawValue.toString()) : null;
                  newField = new FieldInfo(fieldName, fqn);

                  if (longValue != null) {
                     newField.addAnnotation(longValue.toString(), "RESO.OData.Metadata.DefaultValue");
                  }
               } else {
                  newField = new FieldInfo(fieldName, fqn);
               }
            } else if (fieldType.startsWith(EDM_ENUM)) {
               String lookupName = fieldType.substring(EDM_ENUM.length() + 1);
               EnumFieldInfo enumFieldInfo = new EnumFieldInfo(fieldName,
                     EdmPrimitiveTypeKind.Int64.getFullQualifiedName());
               enumFieldInfo.setLookupName(lookupName);
               if (isFlags == true) {
                  enumFieldInfo.setFlags();
               }
               newField = enumFieldInfo;

               ArrayList<GenericGSONobject> lookupList = lookupMap.get(fieldType);
               // Use instance lookupType instead of static
               Boolean isFlagsLookupType = this.lookupType.equals("ENUM_FLAGS"); 

               boolean setFlags = (isFlagsLookupType && lookupList.size() > 1);

               for (GenericGSONobject lookupItem : lookupList) {
                  String enumValueString = (String) lookupItem.getProperty("lookupValue");
                  EnumValueInfo enumValue = new EnumValueInfo(enumValueString);

                  /**
                   * try
                   * {
                   * Long enumLongValue = Long.parseLong(enumValueString);
                   * if (enumLongValue<=0)
                   * {
                   * setFlags = false;
                   * }
                   * else
                   * {
                   * long hob = Long.highestOneBit(enumLongValue);
                   * long lob = Long.lowestOneBit(enumLongValue);
                   * setFlags = (hob==lob);
                   * }
                   * }
                   * catch (Exception e)
                   * {
                   * setFlags = false;
                   * }
                   * /
                   **/

                  ArrayList<AnnotationObject> annotations = null;
                  if (lookupItem.getClass().equals(LookupObject.class)) {
                     annotations = ((LookupObject) lookupItem).getAnnotations();
                  }
                  if (annotations != null) {
                     for (AnnotationObject annotation : annotations) {
                        enumValue.addAnnotation((String) annotation.getProperty("value"),
                              (String) annotation.getProperty("term"));
                     }
                  }

                  enumFieldInfo.addValue(enumValue);
               }

               if (setFlags) {
                  LOG.info("DEBUG: setFlags is " + setFlags);
                  enumFieldInfo.setFlags();
               }

            } else if (fieldType.startsWith(NAMESPACE)) {
               newField = new FieldInfo(fieldName, new FullQualifiedName(NAMESPACE, fieldTypeName));
            }

            if (newField != null) {
               if (nullable != null) {
                  newField.setNullable(nullable);
               }

               if (isCollection) {
                  newField.setCollection();
               }

               // Add Field Annotations
               ArrayList<AnnotationObject> annotations = null;
               if (field.getClass().equals(FieldObject.class)) {
                  annotations = ((FieldObject) field).getAnnotations();
               }

               if (annotations != null) {
                  for (AnnotationObject annotation : annotations) {
                     newField.addAnnotation((String) annotation.getProperty("value"),
                           (String) annotation.getProperty("term"));
                  }
               }

               // Handle type conversions for STRING lookup type
               // Use instance lookupType instead of static
               if (this.lookupType.equals("STRING") && fieldType.equals("Edm.Int64")) {
                  newField.setType(EdmPrimitiveTypeKind.String.getFullQualifiedName());
               }

               // Use instance lookupType instead of static
               if (this.lookupType.equals("STRING") && fieldType.equals("Edm.String") && fieldTypeName != null
                     && (!isExpansion)) {
                  ((FieldInfo) newField).setTypeAttribute(fieldTypeName);
               }

               fieldList.add(newField);
            } else {
               LOG.error("Could not parse type " + fieldType);
            }
         }
      }

      return resources;
   }

   public List<ResourceInfo> readResources() {
      List<ResourceInfo> resources = new ArrayList<ResourceInfo>();

      try {
         List<GenericGSONobject> fields = new ArrayList<GenericGSONobject>();
         List<GenericGSONobject> lookups = new ArrayList<GenericGSONobject>();

         reader.beginObject();
         while (reader.hasNext()) {
            String name = reader.nextName();
            if (HEADER_FIELDS.getOrDefault(name, false)) {
               reader.skipValue();
            } else if (name.equals("fields")) {
               reader.beginArray();
               while (reader.hasNext()) {
                  FieldObject field = readField();
                  fields.add(field);
               }
               reader.endArray();
            } else if (name.equals("lookups")) {
               reader.beginArray();
               while (reader.hasNext()) {
                  LookupObject lookup = readLookup();
                  lookups.add(lookup);
               }
               reader.endArray();
            } else {
               reader.skipValue();
            }
         }
         reader.endObject();

         LOG.info("Read " + fields.size() + " fields, " + lookups.size() + " lookups from file");

         resources = createResources((ArrayList<GenericGSONobject>) fields, (ArrayList<GenericGSONobject>) lookups);
      } catch (IOException e) {
         LOG.error("Error reading resources: ", e);
      }

      return resources;
   }

   public String getLookupType() {
      // Check if lookupType is set, if not fall back to environment variable
      if (this.lookupType != null)
         return this.lookupType;

      // Fall back to environment variable
      String envLookupType = System.getenv().get("LOOKUP_TYPE");
      if (envLookupType == null) {
         LOG.warn("LOOKUP_TYPE environment variable not set, defaulting to STRING");
         return "STRING";
      }
      
      return envLookupType;
   }

   // Handle type conversions for different lookup types
   public String getFieldType(String edmType, String lookupName) {
      // Use instance lookupType instead of static
      switch (this.lookupType) {
         case "ENUM_FLAGS":
         case "ENUM_COLLECTION":
            return "Edm.Int64";
         case "STRING":
         default:
            return "Edm.String";
      }
   }
}

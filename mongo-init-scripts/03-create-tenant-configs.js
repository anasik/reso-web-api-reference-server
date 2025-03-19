print("Starting tenant configuration initialization...");

// Create tenant collection if it doesn't exist
db = db.getSiblingDB("reso");

// Drop the collection if it exists for clean initialization
if (db.getCollectionNames().indexOf("tenant_configs") !== -1) {
  print("Found existing tenant_configs collection, dropping it for clean init");
  db.tenant_configs.drop();
}

// Create sample tenant configurations
db.tenant_configs.insertMany([
  {
    tenantId: "default",
    metadataFilePath: "RESODataDictionary-1.7.metadata-report.json",
    lookupType: "STRING",
    friendlyName: "Default Configuration",
    description:
      "Default configuration using RESO Data Dictionary 1.7 with STRING lookup type",
    isActive: true,
  },
  {
    tenantId: "dd17-string",
    metadataFilePath: "RESODataDictionary-1.7.metadata-report.json",
    lookupType: "STRING",
    friendlyName: "DD 1.7 with String Lookups",
    description:
      "RESO Data Dictionary 1.7 configuration with STRING lookup type",
    isActive: true,
  },
  {
    tenantId: "dd17-enum",
    metadataFilePath: "RESODataDictionary-1.7.metadata-report.json",
    lookupType: "ENUM_COLLECTION",
    friendlyName: "DD 1.7 with Enum Collection",
    description:
      "RESO Data Dictionary 1.7 configuration with ENUM_COLLECTION lookup type",
    isActive: true,
  },
  {
    tenantId: "dd17-flags",
    metadataFilePath: "RESODataDictionary-1.7.metadata-report.json",
    lookupType: "ENUM_FLAGS",
    friendlyName: "DD 1.7 with Enum Flags",
    description:
      "RESO Data Dictionary 1.7 configuration with ENUM_FLAGS lookup type",
    isActive: true,
  },
  {
    tenantId: "dd20-string",
    metadataFilePath: "RESODataDictionary-2.0.metadata-report.json",
    lookupType: "STRING",
    friendlyName: "DD 2.0 with String Lookups",
    description:
      "RESO Data Dictionary 2.0 configuration with STRING lookup type",
    isActive: true,
  },
  {
    tenantId: "dd20-enum",
    metadataFilePath: "RESODataDictionary-2.0.metadata-report.json",
    lookupType: "ENUM_COLLECTION",
    friendlyName: "DD 2.0 with Enum Collection",
    description:
      "RESO Data Dictionary 2.0 configuration with ENUM_COLLECTION lookup type",
    isActive: true,
  },
]);

// Create an index on tenantId
db.tenant_configs.createIndex({ tenantId: 1 }, { unique: true });

// Verify the tenant configurations
const tenantCount = db.tenant_configs.countDocuments();
print(`Created ${tenantCount} tenant configurations`);

print("Tenant configuration initialization complete!");

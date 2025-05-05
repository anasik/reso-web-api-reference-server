const fs = require('fs');
const crypto = require('crypto'); // Needed for generating UUIDs
const MONGO_DB = process.env.MONGO_INITDB_DATABASE;
const METADATA_JSON_FILENAME = process.env.CERT_REPORT_FILENAME;

print(`Inserting lookups from metadata json file ${METADATA_JSON_FILENAME}`);

const jsonData = JSON.parse(fs.readFileSync("/" + METADATA_JSON_FILENAME, 'utf8'));

const certificationReportIds = [
  "c3efc5b75e6642b48a773540ac4e1b79",
  "eb16ff16a865467ab9def36d31b76418"
];

let transformedLookups = [];

// Iterate over each lookup entry and duplicate for each certificationReportId.
jsonData.lookups.forEach(({ lookupName, lookupValue, annotations }) => {
  const standardNameAnnotation = annotations && annotations.find(a => a.term === "RESO.OData.Metadata.StandardName");
  const legacyValueAnnotation = annotations && annotations.find(a => a.term === "RESO.OData.Metadata.LegacyODataValue");

  // Prepare common fields from JSON.
  const baseFields = {
    "LookupName": lookupName.split('.').pop(),
    "LookupValue": lookupValue,
    "StandardLookupValue": standardNameAnnotation ? standardNameAnnotation.value : null,
    "LegacyOdataValue": legacyValueAnnotation ? legacyValueAnnotation.value : null,
    "ModificationTimestamp": ISODate()
  };

  // Duplicate the lookup for each certificationReportId.
  certificationReportIds.forEach(certId => {
    transformedLookups.push({
      // Generate a fresh unique LookupKey for each record.
      "LookupKey": crypto.randomUUID().replace(/-/g, ''),
      ...baseFields,
      "certificationReportId": certId
    });
  });
});

const resoDb = db.getSiblingDB(MONGO_DB);
resoDb.lookup.insertMany(transformedLookups);
print(`Lookups from metadata json file ${METADATA_JSON_FILENAME} inserted successfully`);
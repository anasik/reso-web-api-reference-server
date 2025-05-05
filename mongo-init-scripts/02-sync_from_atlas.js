const sourceUri = process.env.ATLAS_DATAFILL_URI;
const remoteDb = process.env.MONGO_INITDB_DATABASE;

var useFallback = false;
var sourceDB = null;

const collections = [
  "property",
  "media",
  "contact_listing_notes",
  "contact_listings",
  "contacts",
  "history_transactional",
  "internet_tracking",
  "lookup_value",
  "member",
  "office",
  "open_house",
  "other_phone",
  "ouid",
  "property_green_verification",
  "property_power_production",
  "property_rooms",
  "property_unit_type",
  "propsecting",
  "queue",
  "rules",
  "saved_search",
  "showing",
  "social_media",
  "team_members",
  "teams"
];

const sandboxServerIds = ["9d5df754cdf444739661435cd375e51e","2517854a7f604ac88a7ffedef18e4b99",];

// Check if the ATLAS_DATAFILL_URI is provided.
if (!sourceUri) {
  print("No source URI provided. Switching to fallback mode.");
  useFallback = true;
} else {
  try {
    var sourceConn = new Mongo(sourceUri);
    sourceDB = sourceConn.getDB(remoteDb);
    // Test the connection.
    var pingResult = sourceDB.runCommand({ ping: 1 });
    if (!pingResult.ok) {
      throw "Ping to database failed";
    }
    print("Connected to database successfully.");
  } catch (e) {
    print("Error connecting to remote source: " + e + ". Switching to fallback mode.");
    useFallback = true;
  }
}

// Function to fetch fallback data from blob storage.
async function fetchFallbackData(collectionName) {
  var url = "https://resostuff.blob.core.windows.net/refserverfiles/" + collectionName + ".json";
  print("Fetching fallback JSON from: " + url);

  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error("HTTP error " + response.status);
    }
    const data = await response.json();
    return data;
  } catch (err) {
    print("Error fetching or parsing fallback data for " + collectionName + ": " + err);
    return [];
  }
}

// Function to copy a single collection.
async function copyCollection(collectionName) {
  print("Copying collection: " + collectionName);
  var data = [];

  if (!useFallback) {
    try {
      data = sourceDB[collectionName].find().toArray();
      print("Fetched " + data.length + " documents for " + collectionName);
    } catch (e) {
      print("Error fetching data for " + collectionName + ": " + e + ". Falling back.");
      data = await fetchFallbackData(collectionName);
      print("Fetched " + data.length + " fallback documents for " + collectionName);
    }
  } else {
    data = await fetchFallbackData(collectionName);
    print("Fetched " + data.length + " fallback documents for " + collectionName);
  }

  // Process and transform the data:
  // For each document: remove the _id field and duplicate it for each sandboxServerId.
  var transformedData = [];
  data.forEach(function(doc) {
    // Create a shallow copy of the document without the _id field.
    let baseDoc = Object.assign({}, doc);
    delete baseDoc._id;
    // Duplicate for each sandboxServerId.
    sandboxServerIds.forEach(function(serverId) {
      // Clone the base document.
      let newDoc = Object.assign({}, baseDoc, { sandboxServerId: serverId });
      transformedData.push(newDoc);
    });
  });
  print("Transformed " + collectionName + " collection from " + data.length + " documents to " + transformedData.length + " documents.");
  data = transformedData;

  // Insert the data into the local collection.
  if (data && data.length > 0) {
    db[collectionName].insertMany(data);
    print("Inserted " + data.length + " documents into local collection " + collectionName);
  } else {
    print("No documents to insert for " + collectionName);
  }
}

(async function () {
  for (const collectionName of collections) {
    await copyCollection(collectionName);
  }
})();
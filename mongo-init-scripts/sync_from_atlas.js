// Connect to source (Atlas) and copy to local
const sourceUri = "mongodb://localhost:27017/";
const collections = [
    "property",
    "media",
    "contact_listing_notes",
    "contact_listings",
    "contacts",
    "field",
    "history_transactional",
    "internet_tracking",
    "lookup",
    "lookup_value",
    "member",
    "office",
    "open_house",
    "other_phone",
    "ouid",
    "property",
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

// Function to copy data
function copyCollection(collectionName) {
    print(`Copying collection: ${collectionName}`);
    const data = db[collectionName].find().toArray();
    db[collectionName].insertMany(data);
}

// Copy each collection
collections.forEach(copyCollection); 

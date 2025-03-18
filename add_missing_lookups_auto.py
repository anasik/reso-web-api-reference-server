from pymongo import MongoClient
import random
import string
import re
import datetime
import hashlib
import sys
import argparse

# Parse command line arguments
parser = argparse.ArgumentParser(description="Add missing lookup fields to MongoDB")
parser.add_argument("--auto", action="store_true", help="Run automatically without confirmation")
parser.add_argument("--mongo-uri", default="mongodb://localhost:27017/", help="MongoDB URI")
parser.add_argument("--db-name", default="reso", help="Database name")
args = parser.parse_args()

print("Starting missing lookup field addition script...")
print(f"MongoDB URI: {args.mongo_uri}")
print(f"Database: {args.db_name}")

# Connect to MongoDB
try:
    client = MongoClient(args.mongo_uri)
    db = client[args.db_name]
    print("Connected to MongoDB successfully!")
except Exception as e:
    print(f"Failed to connect to MongoDB: {e}")
    sys.exit(1)

# Array of all missing lookup fields
missing_fields = [
    "LockBoxType", "SpecialLicenses", "PowerProductionType", "ReasonActiveOrDisabled", "Fencing",
    "GreenWaterConservation", "LotSizeSource", "ResourceName", "CoListAgentDesignation", "OfficeBranchType", "Languages",
    "GreenSustainability", "OpenHouseStatus", "AreaSource", "BuyerAgentDesignation", "HorseAmenities", "ListingService",
    "LotSizeUnits", "WaterSource", "LaundryFeatures", "Flooring", "Permission", "DoorFeatures", "MediaType",
    "WaterfrontFeatures", "AreaUnits", "Cooling", "PreferredPhone", "ExteriorFeatures", "HoursDaysOfOperation",
    "SecurityFeatures", "Skirt", "ActorType", "PreferredAddress", "LotDimensionsSource", "CoBuyerAgentDesignation",
    "PetsAllowed", "Country", "StreetDirection", "FinancialDataSource", "OccupantType", "OtherStructures",
    "AssociationAmenities", "BodyType", "CurrentFinancing", "PowerProductionAnnualStatus", "SearchQueryType",
    "ObjectType", "ListAgentDesignation", "RoomType", "RuleFormat", "TeamMemberType", "IncomeIncludes", "ImageOf",
    "GreenVerificationSource", "SpaFeatures", "MediaCategory", "ObjectIdType", "RoadSurfaceType", "ExistingLeaseType",
    "ContactListingPreference", "ParkingFeatures", "TeamStatus", "Edm.String", "PoolFeatures", "GreenIndoorAirQuality",
    "Sewer", "GreenVerificationStatus", "Appliances", "Heating", "StructureType", "ContactStatus", "BuyerFinancing",
    "ScheduleType", "OwnerPays", "ContactType", "EventType", "Basement", "CurrentUse", "BusinessType",
    "PatioAndPorchFeatures", "PropertyType", "OfficeStatus", "OfficeType", "RoadFrontageType", "ListingAgreement",
    "RoadResponsibility", "Utilities", "GreenEnergyGeneration", "ChangeType", "FireplaceFeatures", "WindowFeatures",
    "FeeFrequency", "SpecialListingConditions", "CommunityFeatures", "LeaseTerm", "MemberOtherPhoneType",
    "MemberDesignation", "GreenEnergyEfficient", "StateOrProvince", "CommonWalls", "RentIncludes", "NotedBy",
    "QueueTransactionType", "GreenBuildingVerificationType", "CommonInterest", "AssociationFeeIncludes",
    "SocialMediaType", "DeviceType", "SyndicateTo", "UnitTypeType", "AccessibilityFeatures", "ShowingContactType",
    "Vegetation", "CompensationType", "OtherPhoneType", "OwnershipType", "LinearUnits", "DirectionFaces", "Concessions",
    "LotFeatures", "PossibleUse", "Furnished", "StandardStatus", "OpenHouseType", "DailySchedule", "ConstructionMaterials",
    "Roof", "PropertyCondition", "ClassName", "FrontageType", "DevelopmentStatus", "EventTarget", "TenantPays",
    "ShowingRequirements", "ListingTerms", "UnitsFurnished", "Electric", "FoundationDetails", "Attended", "View",
    "TaxStatusCurrent", "PropertySubType", "LeaseRenewalCompensation", "MemberType", "YearBuiltSource", "Possession",
    "InteriorOrRoomFeatures", "Levels", "OtherEquipment", "OperatingExpenseIncludes", "MemberStatus", "LaborInformation"
]

# Function to generate random string
def generate_random_string(length=10):
    characters = string.ascii_letters + string.digits
    return ''.join(random.choice(characters) for _ in range(length))

# Function to generate random hash for LookupKey
def generate_random_hash():
    random_str = generate_random_string(32)
    return hashlib.sha256(random_str.encode()).hexdigest()

# Function to convert camelCase to spaces
def camel_to_spaces(text):
    return re.sub(r'(?<!^)(?=[A-Z])', ' ', text)

# Values for each lookup field (3 values per field)
standard_values = {
    "LockBoxType": ["Electronic", "Combination", "Key"],
    "SpecialLicenses": ["Broker", "Agent", "Appraiser"],
    "PowerProductionType": ["Solar", "Wind", "Geothermal"],
    "ReasonActiveOrDisabled": ["Retired", "Suspended", "Inactive"],
    "Fencing": ["Wood", "Chain Link", "Wrought Iron"],
    "GreenWaterConservation": ["Low Flow", "Rainwater Collection", "Gray Water System"],
    "LotSizeSource": ["Assessor", "Survey", "Measured"],
    "ResourceName": ["Property", "Member", "Office"],
    "CoListAgentDesignation": ["CRS", "GRI", "ABR"],
    "OfficeBranchType": ["Main", "Satellite", "Virtual"],
    "Languages": ["English", "Spanish", "French"],
    "GreenSustainability": ["LEED", "Energy Star", "Green Building"],
    "OpenHouseStatus": ["Active", "Canceled", "Completed"],
    "AreaSource": ["Assessor", "Appraiser", "Builder"],
    "BuyerAgentDesignation": ["CRS", "GRI", "ABR"],
    "HorseAmenities": ["Barn", "Paddock", "Arena"],
    "ListingService": ["MLS", "Private", "Exclusive"],
    "LotSizeUnits": ["Acres", "SquareFeet", "SquareMeters"],
    "WaterSource": ["Municipal", "Well", "Cistern"],
    "LaundryFeatures": ["InUnit", "HookupOnly", "CommonArea"],
    "Flooring": ["Hardwood", "Carpet", "Tile"],
    "Permission": ["ReadOnly", "ReadWrite", "Admin"],
    "DoorFeatures": ["French", "Sliding", "Steel"],
    "MediaType": ["Photo", "Video", "VirtualTour"],
    "WaterfrontFeatures": ["Ocean", "Lake", "River"],
    "AreaUnits": ["SquareFeet", "SquareMeters", "Acres"],
    "Cooling": ["Central", "WindowUnits", "Evaporative"],
    "PreferredPhone": ["Mobile", "Home", "Work"],
    "ExteriorFeatures": ["Deck", "Patio", "Pool"],
    "HoursDaysOfOperation": ["Weekdays", "Weekends", "24Hours"],
    "SecurityFeatures": ["Alarm", "Cameras", "Gated"],
    "Skirt": ["Brick", "Vinyl", "None"],
    "ActorType": ["Individual", "Organization", "System"],
    "PreferredAddress": ["Home", "Work", "Mailing"],
    "LotDimensionsSource": ["Assessor", "Survey", "Measured"],
    "CoBuyerAgentDesignation": ["CRS", "GRI", "ABR"],
    "PetsAllowed": ["Yes", "No", "Restricted"],
    "Country": ["US", "CA", "MX"],
    "StreetDirection": ["N", "S", "E", "W"],
    "FinancialDataSource": ["Owner", "Assessor", "Accountant"],
    "OccupantType": ["Owner", "Tenant", "Vacant"],
    "OtherStructures": ["Shed", "Garage", "Workshop"],
    "AssociationAmenities": ["Pool", "Clubhouse", "Gym"],
    "BodyType": ["Manufactured", "Modular", "SiteBuilt"],
    "CurrentFinancing": ["Conventional", "FHA", "VA"],
    "PowerProductionAnnualStatus": ["Actual", "Estimated", "PartiallyEstimated"],
    "SearchQueryType": ["Property", "Member", "Office"],
    "ObjectType": ["Property", "Member", "Office"],
    "ListAgentDesignation": ["CRS", "GRI", "ABR"],
    "RoomType": ["Bedroom", "Bathroom", "Kitchen"],
    "RuleFormat": ["Standard", "Custom", "Legacy"],
    "TeamMemberType": ["Leader", "Member", "Assistant"],
    "IncomeIncludes": ["Rent", "Utilities", "Parking"],
    "ImageOf": ["Property", "Member", "Office"],
    "GreenVerificationSource": ["LEED", "Energy Star", "HERS"],
    "SpaFeatures": ["Indoor", "Outdoor", "Heated"],
    "MediaCategory": ["Primary", "FloorPlan", "Map"],
    "ObjectIdType": ["MLS", "UUID", "Custom"],
    "RoadSurfaceType": ["Paved", "Gravel", "Dirt"],
    "ExistingLeaseType": ["Annual", "Monthly", "Weekly"],
    "ContactListingPreference": ["Email", "Phone", "Mail"],
    "ParkingFeatures": ["Garage", "Carport", "Street"],
    "TeamStatus": ["Active", "Inactive", "Pending"],
    "Edm.String": ["String1", "String2", "String3"],
    "PoolFeatures": ["Indoor", "Outdoor", "Heated"],
    "GreenIndoorAirQuality": ["Low VOC", "Air Filtration", "Ventilation"],
    "Sewer": ["Municipal", "Septic", "None"],
    "GreenVerificationStatus": ["Complete", "InProcess", "Pending"],
    "Appliances": ["Refrigerator", "Stove", "Dishwasher"],
    "Heating": ["Forced Air", "Radiant", "Baseboard"],
    "StructureType": ["House", "Condo", "Townhouse"],
    "ContactStatus": ["Active", "Inactive", "Lead"],
    "BuyerFinancing": ["Cash", "Conventional", "FHA"],
    "ScheduleType": ["Daily", "Weekly", "Monthly"],
    "OwnerPays": ["HOA", "Utilities", "Insurance"],
    "ContactType": ["Client", "Lead", "Prospect"],
    "EventType": ["Open House", "Tour", "Showing"],
    "Basement": ["Full", "Partial", "None"],
    "CurrentUse": ["Residential", "Commercial", "Mixed"],
    "BusinessType": ["Retail", "Service", "Manufacturing"],
    "PatioAndPorchFeatures": ["Covered", "Screened", "Open"],
    "PropertyType": ["Residential", "Commercial", "Land"],
    "OfficeStatus": ["Active", "Inactive", "Pending"],
    "OfficeType": ["Main", "Branch", "Virtual"],
    "RoadFrontageType": ["Public", "Private", "None"],
    "ListingAgreement": ["Exclusive", "Open", "Variable"],
    "RoadResponsibility": ["Public", "Private", "HOA"],
    "Utilities": ["Electric", "Gas", "Water"],
    "GreenEnergyGeneration": ["Solar", "Wind", "Geothermal"],
    "ChangeType": ["Add", "Update", "Delete"],
    "FireplaceFeatures": ["Wood", "Gas", "Electric"],
    "WindowFeatures": ["Double Pane", "Tinted", "Security"],
    "FeeFrequency": ["Monthly", "Annual", "Quarterly"],
    "SpecialListingConditions": ["Short Sale", "Foreclosure", "Standard"],
    "CommunityFeatures": ["Pool", "Clubhouse", "Tennis"],
    "LeaseTerm": ["Annual", "Monthly", "Seasonal"],
    "MemberOtherPhoneType": ["Mobile", "Home", "Work"],
    "MemberDesignation": ["CRS", "GRI", "ABR"],
    "GreenEnergyEfficient": ["Energy Star", "Solar", "Insulation"],
    "StateOrProvince": ["CA", "TX", "NY"],
    "CommonWalls": ["None", "One", "Two"],
    "RentIncludes": ["Utilities", "Parking", "Cable"],
    "NotedBy": ["System", "User", "Admin"],
    "QueueTransactionType": ["Add", "Update", "Delete"],
    "GreenBuildingVerificationType": ["LEED", "Energy Star", "HERS"],
    "CommonInterest": ["Condominium", "HOA", "PUD"],
    "AssociationFeeIncludes": ["Water", "Trash", "Insurance"],
    "SocialMediaType": ["Facebook", "Twitter", "Instagram"],
    "DeviceType": ["Mobile", "Desktop", "Tablet"],
    "SyndicateTo": ["Zillow", "Realtor", "Trulia"],
    "UnitTypeType": ["Flat", "Townhouse", "Duplex"],
    "AccessibilityFeatures": ["Elevator", "Ramp", "WideDoorways"],
    "ShowingContactType": ["Listing Agent", "Owner", "Tenant"],
    "Vegetation": ["Trees", "Grass", "Desert"],
    "CompensationType": ["Percentage", "Flat Fee", "Split"],
    "OtherPhoneType": ["Mobile", "Home", "Work"],
    "OwnershipType": ["Fee Simple", "Leasehold", "Cooperative"],
    "LinearUnits": ["Feet", "Meters", "Miles"],
    "DirectionFaces": ["North", "South", "East"],
    "Concessions": ["Seller Credit", "Closing Costs", "Repairs"],
    "LotFeatures": ["Corner", "Cul-de-sac", "Wooded"],
    "PossibleUse": ["Residential", "Commercial", "Agricultural"],
    "Furnished": ["Fully", "Partially", "Unfurnished"],
    "StandardStatus": ["Active", "Pending", "Sold"],
    "OpenHouseType": ["Public", "Broker", "Virtual"],
    "DailySchedule": ["Morning", "Afternoon", "Evening"],
    "ConstructionMaterials": ["Wood", "Brick", "Stucco"],
    "Roof": ["Shingle", "Tile", "Metal"],
    "PropertyCondition": ["Excellent", "Good", "Fair"],
    "ClassName": ["Residential", "Commercial", "Land"],
    "FrontageType": ["Road", "Water", "Golf"],
    "DevelopmentStatus": ["Existing", "UnderConstruction", "Proposed"],
    "EventTarget": ["Property", "Member", "Office"],
    "TenantPays": ["Utilities", "Lawn Care", "Repairs"],
    "ShowingRequirements": ["Appointment", "LockBox", "CallFirst"],
    "ListingTerms": ["Cash", "Conventional", "FHA"],
    "UnitsFurnished": ["All", "Some", "None"],
    "Electric": ["Public", "Solar", "Generator"],
    "FoundationDetails": ["Slab", "Crawl Space", "Basement"],
    "Attended": ["Yes", "No", "Optional"],
    "View": ["Water", "Mountain", "City"],
    "TaxStatusCurrent": ["Current", "Delinquent", "Exempt"],
    "PropertySubType": ["SingleFamily", "Condo", "Townhouse"],
    "LeaseRenewalCompensation": ["Full", "Half", "None"],
    "MemberType": ["Agent", "Broker", "Appraiser"],
    "YearBuiltSource": ["Assessor", "Owner", "Estimated"],
    "Possession": ["AtClosing", "Negotiable", "ToBeArranged"],
    "InteriorOrRoomFeatures": ["Fireplace", "Hardwood", "Updated"],
    "Levels": ["One", "Two", "Three"],
    "OtherEquipment": ["Generator", "Security System", "Sprinklers"],
    "OperatingExpenseIncludes": ["Utilities", "Maintenance", "Insurance"],
    "MemberStatus": ["Active", "Inactive", "Pending"],
    "LaborInformation": ["Union", "Non-Union", "Mixed"]
}

# Arrays to hold our new lookup documents and track what we created
lookups_to_insert = []
created_lookups = {}
skipped_fields = []

print("\nChecking lookup collection for existing fields...")
# For each missing field, create random lookup values
for field_name in missing_fields:
    # Skip if field already exists in the lookup collection
    existing_count = db.lookup.count_documents({"LookupName": field_name})
    if existing_count > 0:
        print(f"Field '{field_name}' already exists with {existing_count} values. Skipping.")
        skipped_fields.append(field_name)
        continue
    
    # Get the standard values for this field or use random ones if not defined
    values = standard_values.get(field_name, ["Value1", "Value2", "Value3"])
    
    # Create 3 lookup values for each field
    for value in values:
        lookup_key = generate_random_hash()
        
        # Format the LegacyOdataValue by adding spaces in camel case
        legacy_value = value
        if any(c.isupper() for c in value):
            legacy_value = camel_to_spaces(value)
        
        lookup = {
            "LookupKey": lookup_key,
            "LookupName": field_name,
            "LookupValue": value,
            "StandardLookupValue": value,
            "LegacyOdataValue": legacy_value,
            "ModificationTimestamp": datetime.datetime.now()
        }
        
        lookups_to_insert.append(lookup)
        
        # Track what we created
        if field_name not in created_lookups:
            created_lookups[field_name] = []
        created_lookups[field_name].append(value)

print(f"\n{len(created_lookups)} new fields to add with {len(lookups_to_insert)} total lookup values.")
print(f"{len(skipped_fields)} fields already exist in the database.")

# Insert the lookup documents if we have any
if lookups_to_insert:
    # Either automatically insert or ask for confirmation
    should_insert = args.auto
    
    if not should_insert:
        confirm = input("\nReady to insert the lookup values. Proceed? (y/n): ")
        should_insert = confirm.lower() == 'y'
    
    if should_insert:
        try:
            result = db.lookup.insert_many(lookups_to_insert)
            print(f"\nSuccess! Inserted {len(result.inserted_ids)} lookup values.")
            
            # Print summary of what was created
            print("\nSummary of created lookup values:")
            for field_name, values in created_lookups.items():
                print(f"{field_name}: {', '.join(values)}")
                
            # Print verification instructions
            print("\nTo verify one of the insertions, run this MongoDB command:")
            sample_field = next(iter(created_lookups.keys()))
            print(f"db.lookup.find({{LookupName: '{sample_field}'}}).pretty()")
                
        except Exception as e:
            print(f"\nError inserting lookup values: {e}")
    else:
        print("\nInsertion canceled by user.")
else:
    print("\nNo new lookup values to insert. All fields already exist.")

# Close the MongoDB connection
client.close()
print("\nMongoDB connection closed. Script complete.") 
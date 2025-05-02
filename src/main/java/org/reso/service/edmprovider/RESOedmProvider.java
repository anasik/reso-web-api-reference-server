package org.reso.service.edmprovider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlAbstractEdmProvider;
import org.apache.olingo.commons.api.edm.provider.CsdlAction;
import org.apache.olingo.commons.api.edm.provider.CsdlActionImport;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotation;
import org.apache.olingo.commons.api.edm.provider.CsdlComplexType;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainerInfo;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlEnumMember;
import org.apache.olingo.commons.api.edm.provider.CsdlEnumType;
import org.apache.olingo.commons.api.edm.provider.CsdlFunction;
import org.apache.olingo.commons.api.edm.provider.CsdlFunctionImport;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationPropertyBinding;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.apache.olingo.commons.api.edm.provider.CsdlTypeDefinition;
import org.apache.olingo.commons.api.ex.ODataException;
import org.reso.service.data.meta.AnnotationInfo;
import org.reso.service.data.meta.EnumFieldInfo;
import org.reso.service.data.meta.EnumValueInfo;
import org.reso.service.data.meta.FieldInfo;
import org.reso.service.data.meta.ResourceInfo;
import org.reso.service.tenant.TenantConfig;
import org.reso.service.tenant.TenantContext;
import org.reso.service.tenant.TenantConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RESOedmProvider extends CsdlAbstractEdmProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RESOedmProvider.class);
    
    // Replace static LOOKUP_TYPE with a method to get it from tenant configuration
    // This ensures we use the correct lookup type for the current tenant
    private String getLookupType() {
        String tenantId = TenantContext.getCurrentTenant();
        TenantConfig config = TenantConfigurationService.getTenantConfig(tenantId);
        return config.getLookupType();
    }

    // Container
    public static final String CONTAINER_NAME = "Container";
    public static final FullQualifiedName CONTAINER = new FullQualifiedName("org.reso.metadata", CONTAINER_NAME);

    // Service Namespace
    public static final String NAMESPACE = "org.reso.metadata";
    public static final String EDM_ENUM = NAMESPACE + ".enums";

    // EntitySets
    public static final String ES_FIELD = "Field";
    public static final String ES_LOOKUP = "Lookup";

    // Definitions
    private HashMap<String, ArrayList<CsdlEnumType>> enumTypeDefinitions = new HashMap<>();
    private HashMap<String, ArrayList<CsdlTypeDefinition>> typeDefinitions = new HashMap<>();
    private HashMap<String, ArrayList<CsdlEntityType>> entityTypeDefinitions = new HashMap<>();
    private HashMap<String, ArrayList<CsdlEntitySet>> entitySetDefinitions = new HashMap<>();

    // Content
    private HashMap<String, ResourceInfo> resourceDefinitions = new HashMap<>();

    public RESOedmProvider() {
    }

    @Override
    public CsdlEntityContainer getEntityContainer() throws ODataException {
        // create EntitySets
        List<CsdlEntitySet> entitySets = new ArrayList<CsdlEntitySet>();

        // Add all EntityTypeDefinitions to EntitySets
        for (ArrayList<CsdlEntitySet> entityTypeGroup : entitySetDefinitions.values()) {
            entitySets.addAll(entityTypeGroup);
        }
        // create EntityContainer
        CsdlEntityContainer entityContainer = new CsdlEntityContainer();
        entityContainer.setName(CONTAINER_NAME);
        entityContainer.setEntitySets(entitySets);

        return entityContainer;
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo() throws ODataException {
        // This method is invoked when displaying the service document at
        // e.g. http://localhost:8080/DemoService/DemoService.svc
        CsdlEntityContainerInfo entityContainerInfo = new CsdlEntityContainerInfo();
        entityContainerInfo.setContainerName(CONTAINER);
        return entityContainerInfo;
    }

    @Override
    public List<CsdlSchema> getSchemas() throws ODataException {

        // create Schema
        CsdlSchema schema = new CsdlSchema();
        schema.setNamespace(NAMESPACE);

        // add EnumTypes
        List<CsdlEnumType> enumTypes = new ArrayList<CsdlEnumType>();
        for (ArrayList<CsdlEnumType> enumTypeGroup : enumTypeDefinitions.values()) {
            enumTypes.addAll(enumTypeGroup);
        }
        schema.setEnumTypes(enumTypes);

        // Add TypeDefinitions
        List<CsdlTypeDefinition> tds = new ArrayList<CsdlTypeDefinition>();
        for (ArrayList<CsdlTypeDefinition> typeDefinitionGroup : typeDefinitions.values()) {
            tds.addAll(typeDefinitionGroup);
        }
        schema.setTypeDefinitions(tds);

        // add EntityTypes
        List<CsdlEntityType> entityTypes = new ArrayList<CsdlEntityType>();
        for (ArrayList<CsdlEntityType> entityTypeGroup : entityTypeDefinitions.values()) {
            entityTypes.addAll(entityTypeGroup);
        }
        schema.setEntityTypes(entityTypes);

        // add EntityContainer
        schema.setEntityContainer(getEntityContainer());

        // finally
        List<CsdlSchema> schemas = new ArrayList<CsdlSchema>();
        schemas.add(schema);

        return schemas;
    }

    @Override
    public CsdlEntityType getEntityType(FullQualifiedName entityTypeName) throws ODataException {
        CsdlEntityType entityType = null;

        if (entityTypeName.getNamespace().equals(this.NAMESPACE)) {
            ArrayList<CsdlEntityType> defList = this.entityTypeDefinitions.get(entityTypeName.getName());
            if (defList == null) {
                LOG.error("ERROR: " + entityTypeName.getName() + " not found");
                return null;
            } else if (defList.size() != 1) {
                LOG.error("ERROR: " + entityTypeName.getName() + " has " + defList.size() + " definitions");
                return null;
            }
            entityType = defList.get(0);
        }

        return entityType;
    }

    @Override
    public CsdlEntitySet getEntitySet(FullQualifiedName entityContainer, String entitySetName) throws ODataException {
        CsdlEntitySet entitySet = null;

        if (entityContainer.equals(CONTAINER)) {
            ArrayList<CsdlEntitySet> defList = this.entitySetDefinitions.get(entitySetName);
            if (defList == null) {
                LOG.error("ERROR: " + entitySetName + " not found");
                return null;
            } else if (defList.size() != 1) {
                LOG.error("ERROR: " + entitySetName + " has " + defList.size() + " definitions");
                return null;
            }
            entitySet = defList.get(0);
        }

        return entitySet;
    }

    @Override
    public CsdlEnumType getEnumType(FullQualifiedName enumTypeName) throws ODataException {
        CsdlEnumType enumType = null;

        if (enumTypeName.getNamespace().equals(EDM_ENUM)) {

            ArrayList<CsdlEnumType> defList = this.enumTypeDefinitions.get(enumTypeName.getName());
            if (defList == null) {
                LOG.error("ERROR: " + enumTypeName.getName() + " not found");
                return null;
            } else if (defList.size() != 1) {
                LOG.error("ERROR: " + enumTypeName.getName() + " has " + defList.size() + " definitions");
                return null;
            }
            enumType = defList.get(0);
        }

        return enumType;
    }

    @Override
    public CsdlTypeDefinition getTypeDefinition(FullQualifiedName typeDefinitionName) throws ODataException {
        CsdlTypeDefinition typeDefinition = null;

        if (typeDefinitionName.getNamespace().equals(this.NAMESPACE)) {
            ArrayList<CsdlTypeDefinition> defList = this.typeDefinitions.get(typeDefinitionName.getName());
            if (defList == null) {
                LOG.error("ERROR: " + typeDefinitionName.getName() + " not found");
                return null;
            } else if (defList.size() != 1) {
                LOG.error("ERROR: " + typeDefinitionName.getName() + " has " + defList.size() + " definitions");
                return null;
            }
            typeDefinition = defList.get(0);
        }

        return typeDefinition;
    }

    @Override
    public CsdlComplexType getComplexType(FullQualifiedName complexTypeName) throws ODataException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CsdlAction getAction(FullQualifiedName actionName) throws ODataException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CsdlActionImport getActionImport(FullQualifiedName entityContainer, String actionImportName)
            throws ODataException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CsdlFunction getFunction(FullQualifiedName functionName) throws ODataException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CsdlFunctionImport getFunctionImport(FullQualifiedName entityContainer, String functionImportName)
            throws ODataException {
        // TODO Auto-generated method stub
        return null;
    }

    public List<CsdlEnumMember> getEnumValues(EnumFieldInfo efi) {
        ArrayList<CsdlEnumMember> enums = new ArrayList<CsdlEnumMember>();

        for (EnumValueInfo evi : efi.getEnumValues()) {
            CsdlEnumMember enumMember = new CsdlEnumMember();
            enumMember.setName(evi.getEnumName());
            enumMember.setValue(evi.getEnumValue());

            ArrayList<CsdlAnnotation> annotations = new ArrayList<>();

            for (AnnotationInfo ai : evi.getAnnotations()) {
                CsdlAnnotation annotation = new CsdlAnnotation();
                annotation.setTerm(ai.getTerm());
                annotation.setExpression(ai.getExpression());
                annotations.add(annotation);
            }

            enumMember.setAnnotations(annotations);

            enums.add(enumMember);
        }

        return enums;
    }

    @Override
    public List<CsdlNavigationPropertyBinding> getNavigationPropertyBindings(FullQualifiedName entityContainer,
            String entitySetName, String navigationPropertyName) throws ODataException {
        return null;
    }

    /*
     * Adding methods
     */
    public ResourceInfo getResource(String name) {
        return resourceDefinitions.get(name);
    }

    public void addDefinition(ResourceInfo resource) {
        // Add to Definition Map for reference
        resourceDefinitions.put(resource.getResourceName(), resource);

        String name = resource.getResourceName();
        String entitySetName = resource.getEntitySetName();
        
        LOG.info("Adding resource definition: {}", name);

        FullQualifiedName entityTypeDefinition = new FullQualifiedName(NAMESPACE, name);

        // Add the EnumTypeDefinitions
        for (FieldInfo fi : resource.getFieldList()) {
            if (fi instanceof EnumFieldInfo) {
                // Use tenant-specific lookup type
                if (!getLookupType().equals("STRING")) {
                    EnumFieldInfo enumFieldInfo = (EnumFieldInfo) fi;
                    CsdlEnumType csdlEnum = new CsdlEnumType();
                    csdlEnum.setName(enumFieldInfo.getLookupName());
                    csdlEnum.setIsFlags(enumFieldInfo.isFlags());
                    csdlEnum.setMembers(getEnumValues(enumFieldInfo));
                    csdlEnum.setUnderlyingType(EdmPrimitiveTypeKind.Int64.getFullQualifiedName());

                    // Store the EnumType in the Map for reference by name
                    ArrayList<CsdlEnumType> enumsList = enumTypeDefinitions.get(enumFieldInfo.getLookupName());
                    if (enumsList == null) {
                        enumsList = new ArrayList<CsdlEnumType>();
                        enumTypeDefinitions.put(enumFieldInfo.getLookupName(), enumsList);
                    }
                    enumsList.clear();

                    enumsList.add(csdlEnum);
                }
            }
        }

        // Create Definition-level TypeDefinitions
        // TODO: Type Definitions

        // Create the Entity Type
        CsdlEntityType entity = new CsdlEntityType();
        CsdlEntitySet entitySet = new CsdlEntitySet();

        entity.setName(name);
        entity.setHasStream(false);

        // Set Primary Keys
        ArrayList<CsdlPropertyRef> keys = new ArrayList<CsdlPropertyRef>();

        for (FieldInfo fi : resource.getFieldList()) {
            if (fi.isKey()) {
                CsdlPropertyRef keyRef = new CsdlPropertyRef();
                keyRef.setName(fi.getFieldName());
                keys.add(keyRef);
            }
        }
        entity.setKey(keys);

        // Add Properties
        ArrayList<CsdlProperty> properties = new ArrayList<CsdlProperty>();
        ArrayList<CsdlNavigationProperty> navigationProperties = new ArrayList<CsdlNavigationProperty>();

        for (FieldInfo fi : resource.getFieldList()) {
            if (fi.isExpansion()) {
                // TODO: Handle Expansions
            } else {
                CsdlProperty property = new CsdlProperty();
                property.setName(fi.getFieldName());
                property.setType(fi.getType());
                property.setCollection(fi.isCollection());
                property.setMaxLength(fi.getMaxLength());
                property.setNullable(fi.isNullable());
                property.setPrecision(fi.getPrecision());
                property.setScale(fi.getScale());
                property.setDefaultValue(fi.getDefaultValue());

                // Add Annotations
                ArrayList<CsdlAnnotation> annotations = new ArrayList<>();

                for (AnnotationInfo ai : fi.getAnnotations()) {
                    CsdlAnnotation annotation = new CsdlAnnotation();
                    annotation.setTerm(ai.getTerm());
                    annotation.setExpression(ai.getExpression());
                    annotations.add(annotation);
                }

                property.setAnnotations(annotations);

                properties.add(property);
            }
        }

        entity.setProperties(properties);
        entity.setNavigationProperties(navigationProperties);

        // Store the Entity Type in the Map for reference by name
        ArrayList<CsdlEntityType> entitiesList = entityTypeDefinitions.get(name);
        if (entitiesList == null) {
            entitiesList = new ArrayList<CsdlEntityType>();
            entityTypeDefinitions.put(name, entitiesList);
        }
        entitiesList.clear();

        entitiesList.add(entity);

        // Store the Entity Set in the Map for reference by name
        entitySet.setName(entitySetName);
        entitySet.setType(entityTypeDefinition);

        ArrayList<CsdlEntitySet> entitiesSetList = entitySetDefinitions.get(entitySetName);
        if (entitiesSetList == null) {
            entitiesSetList = new ArrayList<CsdlEntitySet>();
            entitySetDefinitions.put(entitySetName, entitiesSetList);
        }
        entitiesSetList.clear();

        entitiesSetList.add(entitySet);
    }
}

package com.bonitasoft.custompage.snowmobile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.w3c.dom.Node;

import com.bonitasoft.custompage.snowmobile.BdmField.enumRelationType;

public class BdmBusinessObject {

    private static final String CST_XML_INDEX = "index";

    private static final String CST_XML_REFERENCE = "reference";

    private static final String CST_XML_RELATION_FIELD = "relationField";

    private static final String CST_JSON_INDEXES = "indexes";

    private static final String CST_JSON_UNIQUECONSTRAINTS = "uniqueconstraints";

    private static final String CST_JSON_FIELDNAMES = "fieldnames";

    private static final String CST_XML_LENGTH = "length";

    private static final String CST_JSON_FIELDS = "fields";

    private static final String CST_JSON_SQLREFERENCETABLE = "sqlreferencetable";

    private static final String CST_XML_TYPE = "type";

    private static final String CST_JSON_ISRELATIONFIELD = "isrelationfield";

    private static final String CST_XML_COLLECTION = "collection";

    private static final String CST_XML_NULLABLE = "nullable";

    private static final String CST_JSON_SQLCOLNAME = "sqlcolname";

    private static final String CST_XML_NAME = "name";

    private static BEvent EVENT_ILLEGALRELATIONBUSINESSOBJECT = new BEvent(BdmBusinessObject.class.getName(), 1, Level.APPLICATIONERROR,
            "Illegal relation BusinessObject", "A relation between two business object is illegal.", "This relation is ignored", "Check the model, and fix it");

    /**
     * the business Object is referenced in another businessObject. The key is the name (qualified
     * name).
     */
    private final HashMap<String, BdmBusinessObject> setBusinessFather = new HashMap<>();

    /**
     * name - may be in Upper and Lower case
     */
    private String name;
    /** table name */
    private String tableName;

    public List<BdmField> listFields;
    public List<BdmListOfFields> listConstraints = new ArrayList<>();
    public List<BdmListOfFields> listIndexes = new ArrayList<>();

    public String getLogId() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public void addReferencedIn(final BdmBusinessObject bdmBusinessObjectFather) {
        setBusinessFather.put(bdmBusinessObjectFather.getName(), bdmBusinessObjectFather);
    }

    /**
     * return all the father business for this businessObject
     *
     * @return
     */
    public Map<String, BdmBusinessObject> getBusinessFather() {
        return setBusinessFather;
    }

    /**
     * read the BDM from an XML contains
     *
     * @param oneBusinessObjectNode
     * @return
     */
    public void readFromXml(final Node oneBusinessObjectNode, final OperationStatus operationStatus) {
        name = XmlToolbox.getXmlAttribute(oneBusinessObjectNode, "qualifiedName");
        tableName = JdbcTable.getFormatTableName(getTableNameFromBonitaName(name));

        Node childBusinessNode = XmlToolbox.getNextChildElement(oneBusinessObjectNode.getFirstChild());
        while (childBusinessNode != null) {
            if (childBusinessNode.getNodeName().equals(CST_JSON_FIELDS)) {
                readFields(childBusinessNode, operationStatus);
            } else if (childBusinessNode.getNodeName().equals("uniqueConstraints")) {
                readUniqueConstraints(childBusinessNode, operationStatus);
            } else if (childBusinessNode.getNodeName().equals(CST_JSON_INDEXES)) {
                readIndexes(childBusinessNode, operationStatus);
            }
            childBusinessNode = XmlToolbox.getNextChildElement(childBusinessNode.getNextSibling());
        }
    }

    private String getTableNameFromBonitaName(final String name) {
        final int posIndex = name.lastIndexOf('.');
        if (posIndex != -1) {
            return name.substring(posIndex + 1).toLowerCase();
        } else {
            return name.toLowerCase();
        }

    }

    /**
     * @return
     */
    public Map<String, Object> getJsonDescription() {
        final Map<String, Object> description = new HashMap<>();
        description.put(CST_XML_NAME, name);
        // fields
        final ArrayList<HashMap<String, Object>> listJsonField = new ArrayList<>();
        description.put(CST_JSON_FIELDS, listJsonField);
        for (final BdmField field : listFields) {
            final HashMap<String, Object> jsonField = new HashMap<>();
            jsonField.put( CST_XML_NAME, field.getName());
            jsonField.put( CST_JSON_SQLCOLNAME, field.getSqlColName());
            jsonField.put( CST_XML_NULLABLE, Boolean.valueOf(field.nullable));
            jsonField.put( CST_XML_COLLECTION, Boolean.valueOf(field.collection));
            if (field.isRelationField) {
                jsonField.put( CST_JSON_ISRELATIONFIELD, Boolean.TRUE);
                jsonField.put( CST_XML_TYPE, field.relationType);
                jsonField.put( CST_JSON_SQLREFERENCETABLE, field.getSqlReferenceTable());

            } else {
                jsonField.put(CST_JSON_ISRELATIONFIELD, Boolean.FALSE);
                jsonField.put(CST_XML_TYPE, field.fieldType);
                jsonField.put(CST_XML_LENGTH, field.fieldLength);
            }
            listJsonField.add(jsonField);
        }

        // uniqueconstraints
        final ArrayList<HashMap<String, Object>> listJsonConstraints = new ArrayList<>();
        description.put(CST_JSON_UNIQUECONSTRAINTS, listJsonConstraints);
        for (final BdmListOfFields constraints : listConstraints) {
            final HashMap<String, Object> jsonConstraints = new HashMap<>();
            final ArrayList<String> jsonListFieldName = new ArrayList<>();

            jsonConstraints.put(CST_XML_NAME, constraints.name);
            jsonConstraints.put(CST_JSON_FIELDNAMES, jsonListFieldName);
            for (final String fieldName : constraints.getListFields()) {
                jsonListFieldName.add(fieldName);
            }
            listJsonConstraints.add(jsonConstraints);
        }
        // index
        final ArrayList<HashMap<String, Object>> listJsonIndexes = new ArrayList<>();
        description.put(CST_JSON_INDEXES, listJsonIndexes);
        for (final BdmListOfFields index : listIndexes) {
            final HashMap<String, Object> jsonIndex = new HashMap<>();
            final ArrayList<String> jsonListFieldName = new ArrayList<>();

            jsonIndex.put(CST_XML_NAME, index.name);
            jsonIndex.put(CST_JSON_FIELDNAMES, jsonListFieldName);
            for (final String fieldName : index.getListFields()) {
                jsonListFieldName.add(fieldName);
            }
            listJsonIndexes.add(jsonIndex);
        }
        return description;
    }

    /**
     * read the fields part of the BDM
     *
     * @param childFieldsNode
     * @return
     */

    private void readFields(final Node childFieldsNode, final OperationStatus operationStatus) {
        listFields = new ArrayList<>();

        Node fieldNode = XmlToolbox.getNextChildElement(childFieldsNode.getFirstChild());
        while (fieldNode != null) {
            final BdmField field = new BdmField(this);
            if (fieldNode.getNodeName().equals(CST_XML_RELATION_FIELD)) {
                field.isRelationField = true;
                field.setName(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_NAME));
                try {
                    field.relationType = enumRelationType.valueOf(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_TYPE));
                } catch (final IllegalArgumentException e) {
                    operationStatus.addErrorEvent(new BEvent(EVENT_ILLEGALRELATIONBUSINESSOBJECT, "BusinessObject[" + getName() + "] field[" + field.getName() + "] type get["
                            + XmlToolbox.getXmlAttribute(fieldNode, CST_XML_TYPE) + "] expected [" + enumRelationType.AGGREGATION.toString() + ","
                            + enumRelationType.COMPOSITION.toString() + "] in a relationfield."));
                    field.relationType = enumRelationType.AGGREGATION;
                }
                field.fieldType = "LONG";
                field.nullable = "true".equals(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_NULLABLE));
                field.collection = "true".equals(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_COLLECTION));
                field.reference = XmlToolbox.getXmlAttribute(fieldNode, CST_XML_REFERENCE);
                field.referenceSqlTable = getTableNameFromBonitaName(field.reference);

                listFields.add(field);
            }
            if (fieldNode.getNodeName().equals("field")) {
                field.isRelationField = false;
                field.fieldType = XmlToolbox.getXmlAttribute(fieldNode, CST_XML_TYPE);
                field.setName(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_NAME));
                field.fieldLength = XmlToolbox.getXmlAttributeInteger(fieldNode, CST_XML_LENGTH, 0);
                field.nullable = "true".equals(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_NULLABLE));
                field.collection = "true".equals(XmlToolbox.getXmlAttribute(fieldNode, CST_XML_COLLECTION));
                listFields.add(field);
            }
            fieldNode = XmlToolbox.getNextChildElement(fieldNode.getNextSibling());
        }
    }

    /**
     * read the Unique Constraints of the BDM
     *
     * @param childBusinessNode
     * @return
     */
    private void readUniqueConstraints(final Node childBusinessNode, final OperationStatus operationStatus) {

        listConstraints = new ArrayList<>();

        Node constraintNode = XmlToolbox.getNextChildElement(childBusinessNode.getFirstChild());
        while (constraintNode != null) {
            if (constraintNode.getNodeName().equals("uniqueConstraint")) {
                final BdmListOfFields constraint = new BdmListOfFields(this, false);
                constraint.name = XmlToolbox.getXmlAttribute(constraintNode, CST_XML_NAME);
                // read all field
                final Node fieldNamesNode = XmlToolbox.getNextChildElement(constraintNode.getFirstChild());
                if (fieldNamesNode != null) {
                    Node oneFieldNameNode = XmlToolbox.getNextChildElement(fieldNamesNode.getFirstChild());
                    while (oneFieldNameNode != null) {
                        constraint.addField(XmlToolbox.getNodeValue(oneFieldNameNode));
                        oneFieldNameNode = XmlToolbox.getNextChildElement(oneFieldNameNode.getNextSibling());

                    }
                }
                listConstraints.add(constraint);
            }
            constraintNode = XmlToolbox.getNextChildElement(constraintNode.getNextSibling());
        }
    }

    /**
     * read Indexes of the BDM
     *
     * @param childBusinessNode
     * @return
     */
    private void readIndexes(final Node childBusinessNode, final OperationStatus operationStatus) {
        listIndexes = new ArrayList<>();

        Node indexNode = XmlToolbox.getNextChildElement(childBusinessNode.getFirstChild());
        while (indexNode != null) {
            if (indexNode.getNodeName().equals(CST_XML_INDEX)) {
                final BdmListOfFields index = new BdmListOfFields(this, true);
                index.name = XmlToolbox.getXmlAttribute(indexNode, CST_XML_NAME);
                // read all field
                final Node fieldNamesNode = XmlToolbox.getNextChildElement(indexNode.getFirstChild());
                if (fieldNamesNode != null) {
                    Node oneFieldNameNode = XmlToolbox.getNextChildElement(fieldNamesNode.getFirstChild());
                    while (oneFieldNameNode != null) {
                        index.addField(XmlToolbox.getNodeValue(oneFieldNameNode));
                        oneFieldNameNode = XmlToolbox.getNextChildElement(oneFieldNameNode.getNextSibling());

                    }
                }
                listIndexes.add(index);
            }
            indexNode = XmlToolbox.getNextChildElement(indexNode.getNextSibling());
        }
    }

    /**
     * return the name
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    /** return the SqlTableName. It's in lower case anytime */
    public String getSqlTableName() {
        return tableName;
    }

    public List<BdmField> getListFields() {
        return listFields;
    }

    public BdmField getFieldBySqlColumnName(final String colName, boolean acceptStartBy) {
        for (final BdmField bdmField : listFields) {
            if (bdmField.getSqlColName().equalsIgnoreCase(colName)) {
                return bdmField;
            }
        }
        // second chance : the BDM tronque the name : for example, field is "promotionressources" where the colName detected in the table is "promotionres"
        if (acceptStartBy) {
            for (final BdmField bdmField : listFields) {
                if (bdmField.getSqlColName().toUpperCase().startsWith(colName.toUpperCase())) {
                    return bdmField;
                }
            }
            
        }
        return null;
    }

    public BdmField getFieldByFieldName(final String fieldName) {
        for (final BdmField bdmField : listFields) {
            if (bdmField.getFieldName().equals(fieldName)) {
                return bdmField;
            }
        }
        return null;
    }

    public List<BdmListOfFields> getListConstraints() {
        return listConstraints;
    }

    public List<BdmListOfFields> getListIndexes() {
        return listIndexes;
    }

    public BdmListOfFields addIndex() {
        return new BdmListOfFields(this, true);
    }
    /**
     * the bdm can refere a another BDM as a reference (COMPOSITION or AGREGATION). Search this fields
     *
     * @return
     */
    public BdmField getFieldReference(final String referenceName) {
        for (final BdmField bdmField : listFields) {
            if (referenceName.equals(bdmField.getReference())) {
                return bdmField;
            }
        }
        return null;
    }

}

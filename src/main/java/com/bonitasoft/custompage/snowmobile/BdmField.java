package com.bonitasoft.custompage.snowmobile;

/**
 * BdmField. One file in the BusinessDataModel object
 */

public class BdmField {

    // None is here to help development, to give a value in case this is not a relation
    // AGGREGATION is a Foreign Key
    // COMPOSITION is a child table
    enum enumRelationType {
        COMPOSITION, AGGREGATION
    };

    /**
     *
     */
    public boolean isRelationField;
    public enumRelationType relationType;
    /**
     * name is too touchy to let it with a public access
     */
    private String name;
    public boolean nullable;
    public boolean collection;
    /**
     * in case of relation, this field is the reference field
     */
    public String reference;
    String referenceSqlTable;
    public String fieldType;
    public int fieldLength;

    private final BdmBusinessObject businessObject;

    public BdmField(final BdmBusinessObject bdmBusinessObject) {
        businessObject = bdmBusinessObject;
        assert bdmBusinessObject != null;
    };

    public void setName(String name) {
        this.name = JdbcTable.getFormatColName(name);
    }

    public String getName() {
        return name;
    }

    public String getLogId() {
        return name + "(" + fieldType
                + (collection ? "-collection" : "")
                + ") ";
    }

    @Override
    public String toString() {
        return name + "(" + fieldType + ") " + (nullable ? "null" : "") + (collection ? "-collection" : "-notcollection")
                + (isComposition() ? "-compo" : "-notcompo") + (isAggregation() ? "-aggre" : "-notaggre");
    }

    /**
     * return the column name in lower case
     * it must be prefixed by pid if the field is a relation BUT not a collection. A Collection is managed by an another table
     *
     * @return
     */
    public String getSqlColName() {
        return name + (isRelationField && ! isCollection() ? GeneratorSql.cstSuffixColumnPid : "");
    }

    /**
     * the fieldName is the same as the name
     * @return
     */
    public String getFieldName() {
        return name;
    }

    public String getSqlCompleteColName() {
        return businessObject.getSqlTableName() + "." + name + (isRelationField ? GeneratorSql.cstSuffixColumnPid : "");
    }

    public String getSqlTableName() {
        return businessObject.getSqlTableName();
    }

    public String getReference() {
        return reference;
    };

    public String getSqlReferenceTable() {
        return referenceSqlTable == null ? null : referenceSqlTable.toLowerCase();
    };

    public BdmBusinessObject getBusinessObject() {
        return businessObject;
    }

    public boolean isAggregation() {
        return BdmField.enumRelationType.AGGREGATION.equals(relationType);
    }

    public boolean isComposition() {
        return BdmField.enumRelationType.COMPOSITION.equals(relationType);
    }

    public boolean isCollection() {
        return collection;
    }

}

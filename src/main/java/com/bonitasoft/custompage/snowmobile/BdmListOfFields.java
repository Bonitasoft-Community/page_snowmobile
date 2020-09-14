package com.bonitasoft.custompage.snowmobile;

import java.util.HashSet;
import java.util.Set;

import com.bonitasoft.custompage.snowmobile.JdbcTable.TableListOfColums;

// this class is use to store a Index or a Constraints
public class BdmListOfFields {

    public String name;
    public boolean isIndex;
    private final Set<String> setFieldsName = new HashSet<>();

    BdmBusinessObject businessObject;

    public BdmListOfFields(final BdmBusinessObject businessObject, final boolean isIndex) {
        this.businessObject = businessObject;
        this.isIndex = isIndex;
    }

    public String getLogId() {
        return name;
    }

    public String getSqlName() {
        return JdbcTable.getFormatListNames(name);
    }

    public void addField(final String fieldName) {
        setFieldsName.add(JdbcTable.getFormatColName(fieldName));
    }

    public Set<String> getListFields() {
        return setFieldsName;
    }

    public BdmBusinessObject getBusinessObject() {
        return businessObject;
    }
    
    
    /**
     * compare index : the list of col must the the same.
     *
     * @param bdmIndex
     * @param tableIndex
     * @return
     */
    public boolean compareList(final TableListOfColums tableIndex) {
        if (getListFields().size() != tableIndex.getListColumns().size()) {
            return false;
        }
        for (final String fieldIndexName : getListFields()) {
            // compare with the bdmField.getSqlColName : the field maybe customerId but if this is a Relation field, the sqlField is customerId_pid
            final BdmField bdmField = getBusinessObject().getFieldByFieldName(fieldIndexName);
            if (bdmField != null) {
                if (!tableIndex.getListColumns().contains(bdmField.getSqlColName())) {
                    return false;
                }
            } else {
                if (!tableIndex.getListColumns().contains(fieldIndexName)) {
                    return false;
                }

            }
        }
        return true;
    }
    
    
}
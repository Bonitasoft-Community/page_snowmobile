package com.bonitasoft.custompage.snowmobile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;

import com.bonitasoft.custompage.snowmobile.GeneratorConversionTables.ConversionItem;
import com.bonitasoft.custompage.snowmobile.GeneratorConversionTables.ConversionTableResult;
import com.bonitasoft.custompage.snowmobile.JdbcTable.JdbcColumn;
import com.bonitasoft.custompage.snowmobile.SnowMobileAccess.POLICY_CHANGE_TYPE;
import com.bonitasoft.custompage.snowmobile.SnowMobileAccess.ParametersCalcul;

/**
 * this class genere some SQL
 */
public class GeneratorSql {

    private static BEvent EVENT_TYPEBDM_UNKNOW = new BEvent(GeneratorSql.class.getName(), 2, Level.APPLICATIONERROR,
            "Type Unknow in the BDM", "This type is unknown.", "The SQL Script can't be generated", "Check the XML file");
    private static BEvent EVENT_TYPEJDBC_UNKNOW = new BEvent(GeneratorSql.class.getName(), 2, Level.APPLICATIONERROR,
            "Jdbc Type Unknow", "This type is unknown in JDBC.", "The SQL Script can't be generated", "Check the XML file");

    public BdmContent bdmContent;
    public JdbcModel metaModel;
    public OperationStatus operationStatus;
    public ParametersCalcul parametersCalcul;
    private final HashSet<String> keepDropTables = new HashSet<String>();

    public final static String cstSuffixColumnOrder = "_order";
    public final static String cstSuffixColumnPid = "_pid";
    public final static String cstSuffixColumnPersistenceId = "_persistenceid";
    public final static String cstColumnPersistenceId = "persistenceid";
    public final static String cstColumnPersistenceVersion = "persistenceversion";

    public GeneratorSql(final ParametersCalcul parametersCalcul, final BdmContent bdmContent, final JdbcModel metaModel, final OperationStatus operationStatus) {
        this.bdmContent = bdmContent;
        this.metaModel = metaModel;
        this.operationStatus = operationStatus;
        this.parametersCalcul = parametersCalcul;
    }

    public ConversionItem[] conversionTable;

    public void initialise() {
        GeneratorConversionTables conversion = new GeneratorConversionTables();
        ConversionTableResult conversionResult = conversion.getConversionTable( metaModel.getDatabaseProductName() );
        operationStatus.addErrorEvents(conversionResult.listEvents);
        conversionTable = conversionResult.conversionTable;
    }
    protected void addComment(final String comment) {
        operationStatus.addCommentUpdate(comment);

    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Table */
    /*                                                       */
    /* ----------------------------------------------------- */

    protected void sqlGenerateTable(final BdmBusinessObject bdmContent) {
        final StringBuilder sqlCreate = new StringBuilder();
        sqlCreate.append("create table " + bdmContent.getSqlTableName() + " (\n");
        sqlCreate.append("  " + cstColumnPersistenceId + " bigint NOT NULL,\n");
        sqlCreate.append("  " + cstColumnPersistenceVersion + " bigint,\n");

        for (final BdmField bdmField : bdmContent.listFields) {
            if (!bdmField.isCollection()) {
                sqlCreate.append("  " + getSqlCreate(bdmField) + ",\n");
            }
        }
        sqlCreate.append("  constraint " + bdmContent.getSqlTableName() + "_pkey primary key(" + cstColumnPersistenceId + ")\n");

        sqlCreate.append(");");

        operationStatus.addSqlUpdate(sqlCreate.toString(), false, null);

        // add all index
        for (final BdmListOfFields bdmIndex : bdmContent.listIndexes) {
            sqlCreateIndex(bdmIndex);
        }
        // add all constraint

        for (final BdmListOfFields bdmIndex : bdmContent.listConstraints) {
            sqlCreateIndex(bdmIndex);
        }

        // generate collection
        for (final BdmField bdmField : bdmContent.listFields) {
            if (bdmField.isCollection() && !bdmField.isComposition()) {
                sqlCreateCollection(bdmField);
            }
        }
    }

    public void sqlDropTable(final String tableName) {
        operationStatus.addSqlUpdate("drop table " + tableName + ";", parametersCalcul.commentDropTable, null);
        keepDropTables.add(tableName);
    }

    public void sqlDropTable(final String tableName, final boolean isComment) {
        operationStatus.addSqlUpdate("drop table " + tableName + ";", isComment, null);
        keepDropTables.add(tableName);
    }

    public HashSet<String> getDropTables() {
        return keepDropTables;
    };

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Column */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void sqlCreateColumn(final BdmField bdmField, final boolean isComment) {
        operationStatus.addSqlUpdate("alter table " + bdmField.getBusinessObject().getSqlTableName() + " add " + getSqlCreate(bdmField) + ";", isComment, null);
        // is this is a Aggregation(foreign Key OR a Composition Unique ==> Add the foreign key (a composition - non collection is an agregation in fact
        if (bdmField.isAggregation() || bdmField.isComposition() && !bdmField.isCollection()) {
            // add the contrainst
            sqlCreateForeignConstraint(bdmField, isComment);
        }
    }

    public void sqlColumnToNullable(final BdmField bdmField) {
        operationStatus.addSqlUpdate(
                " alter table "
                        + getSqlTableName(bdmField)
                        + " alter column "
                        + getSqlName(bdmField, false)
                        + " "
                        + getSqlType(bdmField)
                        + " "
                        + " NULL",
                false, null);
    }

    public void sqlColumnToNotNullable(final BdmField bdmField) {
        operationStatus.addSqlUpdate(
                " alter table "
                        + getSqlTableName(bdmField)
                        + " alter column "
                        + getSqlName(bdmField, false)
                        + " "
                        + getSqlType(bdmField)
                        + " "
                        + " NOT NULL",
                false, null);

    }

    public void sqlColumnLengthChange(final BdmField bdmField) {
        // ALTER TABLE [mytable] ALTER COLUMN data varchar(4000);
        operationStatus.addSqlUpdate("alter table " + getSqlTableName(bdmField) + " alter column " + getSqlName(bdmField, false)+" "+ getSqlUpdateType(bdmField) + ";", false, null);
    }

    public void sqlColumnTypeChange(final BdmField bdmField, JdbcColumn currentJdbcColumn ) {
        // try to alter it
        if (parametersCalcul.policyChangeColumnType == POLICY_CHANGE_TYPE.ALTER_COLUMN) {
            operationStatus.addCommentUpdate("------- Column Type change FROM "+this.getSqlType( currentJdbcColumn)+" to "+this.getSqlType(bdmField)+" (alter column policy)");
            operationStatus.addSqlUpdate("alter table " + getSqlTableName(bdmField) + " alter column " + getSqlName(bdmField, false)+" " + getSqlUpdateType(bdmField) + ";",
                    !parametersCalcul.calculChangeTypeColumn, null);
        }
        else if (parametersCalcul.policyChangeColumnType == POLICY_CHANGE_TYPE.IGNORE) {
            operationStatus.addCommentUpdate("------- Column Type change FROM "+this.getSqlType( currentJdbcColumn)+" to "+this.getSqlType(bdmField)+" (ignore policy)");
            
        } else            
        {
            // else drop it
            operationStatus.addCommentUpdate("------- Column Type change FROM "+this.getSqlType( currentJdbcColumn)+" to "+this.getSqlType(bdmField)+" (drop/add policy)");
            sqlDropColumn(bdmField.getBusinessObject().getSqlTableName(), bdmField.getSqlColName(), parametersCalcul.calculChangeTypeColumn);
            sqlCreateColumn(bdmField, parametersCalcul.calculChangeTypeColumn);                
        }
            
     
    }

    public void sqlIsForeignKey(final BdmField bdmField) {
        // the name change : before it was "mycustomer", now it's "mycustomer_pid"

        // operationStatus.addSqlUpdate("alter table "+ bdmField.getBusinessObject().getSqlTableName()+" drop column "+bdmField.getSqlColName()+";", false);
        operationStatus.addSqlUpdate("alter table " + bdmField.getBusinessObject().getSqlTableName() + " create column " + getSqlCreate(bdmField) + ";", false, null);
        sqlCreateForeignConstraint(bdmField, false);
    }

    public void sqlIsNotForeignKey(final BdmField bdmField, final JdbcColumn jdbcColumn) {
        sqlDropForeignConstraint(bdmField, jdbcColumn.contraintsName);
        // the new field may be a new foreign field to a new table (so end by _pid) or a new field.
        // drop the foreign name
        operationStatus.addSqlUpdate("alter table " + bdmField.getBusinessObject().getSqlTableName() + " drop column " + jdbcColumn.getColName() + ";", false, null);
    }

    /**
     * remove a column
     *
     * @param tableName
     * @param colName
     */
    public void sqlDropColumn(final String tableName, final String colName, final boolean isComment) {
        operationStatus.addSqlUpdate("alter table " + tableName + " drop column " + colName + ";", isComment, null);

    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Collection */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void sqlColumnToCollection(final BdmField bdmField) {
        final CollectionAttributes collectionAttributes = sqlCreateCollection(bdmField);

        // attention : the bdmField.getSqlColName can contains _pid if this is a relation, and we dont want that to create the table name

        // copy all data
        final StringBuilder createTableScript = new StringBuilder();
        createTableScript.append("insert into " + collectionAttributes.tableName + " (" + collectionAttributes.columnId + ",");
        createTableScript.append(collectionAttributes.columValue + ",");
        createTableScript.append(collectionAttributes.columnOrder + ") ");
        createTableScript.append("select " + cstColumnPersistenceId + ", " + bdmField.getSqlColName() + ",1 from " + bdmField.getSqlTableName());

        operationStatus.addSqlUpdate(createTableScript.toString(), false, null);

        // 	field can be drop now
        sqlDropColumn(bdmField.getSqlTableName(), bdmField.getSqlColName(), parametersCalcul.commentDropColumn);
    }

    public void sqlCollectionToColumn(final BdmField bdmField) {
        final String collectionTableName = bdmField.getSqlReferenceTable() + "_" + bdmField.getSqlColName();
        sqlCreateCollection(bdmField);
        operationStatus.addSqlUpdate(
                "update " + getSqlName(bdmField, true) + " set (select " + getSqlName(bdmField, true) + " from " + bdmField.getSqlReferenceTable()
                        + " where order = 1)",
                false, null);
        sqlDropTable(collectionTableName);
    }

    public String getCollectionTableName(final BdmField bdmField) {
        // if this is a relation, then the slqColName will finish by _pid and we don't want that
        if (bdmField.isRelationField) {
            return bdmField.getBusinessObject().getSqlTableName() + "_" + bdmField.getName();
        } else {
            return bdmField.getBusinessObject().getSqlTableName() + "_" + bdmField.getSqlColName();
        }
    }

    /**
     * create the collection table
     *
     * @param bdmField
     */
    // the collection table attributes change if the field is a foreign key or not ( ! ) so the generator will return the differentes attribut of the table
    public class CollectionAttributes {

        public String tableName;
        public String columnId;
        public String columValue;
        public String columnOrder;
    }

    public CollectionAttributes sqlCreateCollection(final BdmField bdmField) {
        final CollectionAttributes collectionAttributes = new CollectionAttributes();
        collectionAttributes.tableName = getCollectionTableName(bdmField);
        if (bdmField.isRelationField) {
            collectionAttributes.columnId = bdmField.getSqlTableName() + cstSuffixColumnPid;
            collectionAttributes.columValue = bdmField.getSqlReferenceTable() + cstSuffixColumnPid;
        } else {
            collectionAttributes.columnId = bdmField.getSqlTableName() + cstSuffixColumnPersistenceId;
            collectionAttributes.columValue = bdmField.getSqlColName();
        }
        collectionAttributes.columnOrder = bdmField.getName() + cstSuffixColumnOrder;

        final ConversionItem conversionItem = getConversionItemFromType("INTEGER");

        final StringBuilder sqlCreate = new StringBuilder();
        sqlCreate.append("create table " + collectionAttributes.tableName + " (\n");
        sqlCreate.append("  " + collectionAttributes.columnId + " bigint NOT NULL,\n");

        sqlCreate.append("  " + collectionAttributes.columValue + " " + getSqlType(bdmField) + ",\n");

        sqlCreate.append("  " + collectionAttributes.columnOrder + " " + (conversionItem == null ? "integer" : conversionItem.jdbcSqlType) + " not null,\n");

        sqlCreate.append("  constraint " + collectionAttributes.tableName + "_pkey primary key (" + collectionAttributes.columnId + ","
                + collectionAttributes.columnOrder + "),\n");
        sqlCreate.append("  constraint " + collectionAttributes.tableName + "_fk foreign key (" + collectionAttributes.columnId
                + ") references " + bdmField.getBusinessObject().getSqlTableName() + "(" + cstColumnPersistenceId + ")\n);");

        operationStatus.addSqlUpdate(sqlCreate.toString(), false, null);

        if (bdmField.isAggregation()) {
            // add the foreign key
            final StringBuilder script = new StringBuilder();
            script.append("alter table " + collectionAttributes.tableName);
            script.append(" add constraint fk_" + collectionAttributes.columValue + " foreign key(" + collectionAttributes.columValue + ") references "
                    + bdmField.getSqlReferenceTable() + " (" + cstColumnPersistenceId + ");");
            operationStatus.addSqlUpdate(script.toString(), false, null);
        }
        return collectionAttributes;
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Index */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void sqlCreateIndex(final BdmListOfFields bdmIndex) {
        operationStatus.addSqlUpdate("create index " + bdmIndex.name.toLowerCase() + " on " + bdmIndex.getBusinessObject().getSqlTableName() + " ("
                + getListFields(bdmIndex.getListFields()) + ");", false, null);
    }

    public void sqlDropIndex(final String tableName, final String indexName) {
        operationStatus.addSqlUpdate("drop index " + indexName.toLowerCase() + " on " + tableName + ";", parametersCalcul.commentDropIndex, null);
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Constraint */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void sqlCreateConstraint(final BdmListOfFields bdmConstraint) {
        // ALTER TABLE Customer ADD CONSTRAINT Con_First UNIQUE (Address);
        final StringBuilder script = new StringBuilder();
        script.append("alter table " + bdmConstraint.getBusinessObject().getSqlTableName());
        script.append(" add constraint " + bdmConstraint.getSqlName());
        script.append(" (" + getListFields(bdmConstraint.getListFields()) + ");");
        operationStatus.addSqlUpdate(script.toString(), false, null);
    }

    public void sqlDropConstraint(final String tableName, final String constraintName) {
        final StringBuilder script = new StringBuilder();
        script.append("alter table " + tableName+ " drop constraint " + constraintName + ";");

        operationStatus.addSqlUpdate(script.toString(), parametersCalcul.commentDropConstraint, null);

    }

    public void sqlCreateForeignConstraint(final BdmField bdmField, final boolean isComment) {
        final StringBuilder script = new StringBuilder();

        script.append("alter table " + bdmField.getBusinessObject().getSqlTableName());
        script.append(" add constraint fk_" + bdmField.getSqlColName() + " foreign key(" + bdmField.getSqlColName() + ") references "
                + bdmField.getSqlReferenceTable() + " (" + cstColumnPersistenceId + ");");

        operationStatus.addSqlUpdate(script.toString(), isComment, null);
    }

    public void sqlDropForeignConstraint(final BdmField bdmField, final String constraintName) {
        sqlDropConstraint(bdmField.getBusinessObject().getSqlTableName(), constraintName);

    }

    /*----------------------------------------------------- */
    /*                                                      */
    /* Private toolbox method */
    /*                                                      */
    /*----------------------------------------------------- */

    private String getSqlTableName(final BdmField bdmField) {
        return bdmField.getBusinessObject().getSqlTableName();
    }

    private String getSqlName(final BdmField bdmField, final boolean completeName) {
        if (completeName) {
            return bdmField.getBusinessObject().getSqlTableName() + "." + bdmField.getSqlColName();
        }
        return bdmField.getSqlColName();
    }

    private String getSqlCreate(final BdmField bdmField) {
        return bdmField.getSqlColName() + " " + getSqlType(bdmField);
    }

    /*
     * from a set of String, return it on a list field1,field2,field3
     */
    private String getListFields(final Set<String> listFields) {
        String list = "";
        for (final String field : listFields) {
            list += field + ",";
        }
        if (list.length() > 0) {
            return list.substring(0, list.length() - 1);
        }
        return list;
    }

    /*----------------------------------------------------- */
    /*                                                      */
    /* Conversion */
    /*                                                      */
    /*----------------------------------------------------- */
   

  
  
    /**
     * get the sql type of one field, according the database
     *
     * @param bdmField
     * @return
     */
    public String getSqlType(final BdmField bdmField) {
        final ConversionItem oneConversion = getConversionItemFromType(bdmField.fieldType);
        if (oneConversion != null) {
            return oneConversion.jdbcSqlType + (oneConversion.lengthIsImportant ? "(" + (oneConversion.overrideLength != null ? oneConversion.overrideLength : bdmField.fieldLength) + ")" : "");
        }
        operationStatus.addErrorEvent( new BEvent(EVENT_TYPEBDM_UNKNOW, "Type unknow[" + bdmField.fieldType + "] BusinessObject[" + bdmField.getBusinessObject().getName() + "] field[" + bdmField.getName()
                + "]"));
        return "varchar(10)";
    }
    
    /**
     * some database (like Postgres) use a different wording to CREATE and to UPDATE
     * example : create table ...( purpose character varying(1024)
     * alter table travel alter column purpose TYPE varchar(1024)
     * @param bdmField
     * @return
     */
    public String getSqlUpdateType(final BdmField bdmField) {
        final ConversionItem oneConversion = getConversionItemFromType(bdmField.fieldType);
        if (oneConversion != null) {
            return oneConversion.jdbcUpdateSqlType + (oneConversion.lengthIsImportant ? "(" + (oneConversion.overrideLength != null ? oneConversion.overrideLength : bdmField.fieldLength) + ")" : "");
        }
        operationStatus.addErrorEvent( new BEvent(EVENT_TYPEBDM_UNKNOW, "Type unknow[" + bdmField.fieldType + "] BusinessObject[" + bdmField.getBusinessObject().getName() + "] field[" + bdmField.getName()
                + "]"));
        return "varchar(10)";
    }

    public String getSqlType(final JdbcColumn jdbcColumn) {
       return getSqlType( jdbcColumn, true);
    }
    
    /**
     * For one jdbc Column (like VARCHAR, or DOUBLE), multiple type fit. So, all type should be returned, to verify if the expected one match
     * @param jdbcColumn
     * @return
     */
    public List<String> getAllSqlTypes(final JdbcColumn jdbcColumn, boolean collectError) {
        List<String> listAllTypes= new ArrayList<>();
        for (final ConversionItem oneConversion : conversionTable) {
            if (oneConversion.jdbcType == jdbcColumn.dataType) {
                listAllTypes.add(oneConversion.jdbcSqlType + (oneConversion.lengthIsImportant ? "(" + (oneConversion.overrideLength != null ? oneConversion.overrideLength : jdbcColumn.length) + ")" : ""));
            }
        }
        if (collectError && listAllTypes.isEmpty()) {
            operationStatus.addErrorEvent( new BEvent(EVENT_TYPEJDBC_UNKNOW, "Jdbc Type unknow[" + jdbcColumn.dataType + "] JdbcTable[" + jdbcColumn.getJdbcTable().getTableName() + "] field["
                    + jdbcColumn.getColName() + "]"));
        }
        return listAllTypes;
     }
    public String getSqlType(final JdbcColumn jdbcColumn, boolean collectError) {
        for (final ConversionItem oneConversion : conversionTable) {
            if (oneConversion.jdbcType == jdbcColumn.dataType) {
                return oneConversion.jdbcSqlType + (oneConversion.lengthIsImportant ? "(" + (oneConversion.overrideLength != null ? oneConversion.overrideLength : jdbcColumn.length) + ")" : "");
            }
        }
        if (collectError) {
            operationStatus.addErrorEvent( new BEvent(EVENT_TYPEJDBC_UNKNOW, "Jdbc Type unknow[" + jdbcColumn.dataType + "] JdbcTable[" + jdbcColumn.getJdbcTable().getTableName() + "] field["
                    + jdbcColumn.getColName() + "]"));
            return "varchar";
        }
        // do not collect error, so return a null value
        return null;
            
    }
    /**
     * from the JdbcCoum
     * @param jdbcColumn
     * @return
     */
    public String getBDMType( final JdbcColumn jdbcColumn) {
        for (final ConversionItem oneConversion : conversionTable) {            
            if (oneConversion.jdbcType == jdbcColumn.dataType) {
                return oneConversion.bdmType;
            }
        }
        operationStatus.addErrorEvent( new BEvent(EVENT_TYPEJDBC_UNKNOW, "Jdbc Type unknow[" + jdbcColumn.dataType + "] JdbcTable[" + jdbcColumn.getJdbcTable().getTableName() + "] field["
                + jdbcColumn.getColName() + "]"));
        return "";
    }
    
    public ConversionItem getConversionItemFromType(final String type) {
        for (final ConversionItem oneConversion : conversionTable) {
            if (oneConversion.bdmType.equals(type)) {
                return oneConversion;
            }
        }
        return null;
    }

   
}

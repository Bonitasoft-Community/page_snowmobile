package com.bonitasoft.custompage.snowmobile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;

import com.bonitasoft.custompage.snowmobile.SnowMobileAccess.ParametersCalcul;

public class JdbcTable {

    private static Logger logger = Logger.getLogger(JdbcTable.class.getName());

    private static final BEvent EVENT_INCOHERENT_DATABASE = new BEvent(JdbcTable.class.getName(), 1, Level.APPLICATIONERROR, "Table referenced in CATALOG, but no description", "A table is detected in the CATALOG of the database, but then no columns are found", "Differential can't be calculated", "Check the database");

    private static BEvent EVENT_FOREIGNCONSTRAINTWITHOUTNAME = new BEvent(JdbcTable.class.getName(), 2, Level.APPLICATIONERROR,
            "Foreign key without a constraint", "For a foreign key, a constraint is expected", "The constraint will be proposed", "Check the SQL about this key");
    private static BEvent EVENT_SQLERROR = new BEvent(JdbcTable.class.getName(), 3, Level.ERROR,
            "Exception error in SQL", "An exvception arrived during a SQL operation", "The analysis of the database failed", "Check the SQL Exception");

    private final JdbcModel jdbcModel;
    /**
     * the complete Jdbc tableName, in lower case. This table name is use in the complete calcul.
     */
    private final String tableName;
    /**
     * keep the original table name, because when we want to query to search column, then this
     * information is needed.
     */
    private final String sqlOriginaltableName;

    /**
     * in case of a sub table, the childTableName contains the name of the child. Example : table is
     * "customer_address", childTableName is "address"
     */

    private String childTableName;

    public class JdbcColumn {

        // Name is too touchy to let it public
        private String colName;
        public int dataType;
        public int length;
        public boolean nullable;
        public boolean isForeignKey = false;
        // the column is a Reference Key to this table
        public String referenceTable;
        public String contraintsName;
        public JdbcTable jdbcTable;

        public JdbcColumn(final JdbcTable jdbcTable) {
            this.jdbcTable = jdbcTable;
        }

        public void setColName(String name) {
            this.colName = JdbcTable.getFormatColName(name);
        }

        public String getColName() {
            return colName;
        }

        public String getSqlType() {
            return "varchar";
        }

        public int getSqlDataType() {
            return dataType;
        }
        public JdbcTable getJdbcTable() {
            return jdbcTable;
        };

        @Override
        public String toString() {
            return jdbcTable + "." + colName + "(" + dataType + ")";
        };

    }

    
    private List<JdbcColumn> listTechnicalColumns = new ArrayList<JdbcColumn>();
    private List<JdbcColumn> listColumns = new ArrayList<JdbcColumn>();

    public class TableListOfColums {

        public String name;
        public boolean unique;
        public boolean isIndex;
        private final Set<String> setColumns = new HashSet<String>();

        public TableListOfColums(final boolean isIndex) {
            this.isIndex = isIndex;
        };

        @Override
        public String toString() {
            return name + "(" + setColumns + ")";
        }

        public void addColumns(final String colName) {
            setColumns.add(JdbcTable.getFormatColName(colName));
        }

        public Set<String> getListColumns() {
            return setColumns;
        }

    }

    /**
     * index. Key must be in lower case
     */
    private final Map<String, TableListOfColums> indexes = new HashMap<String, TableListOfColums>();

    /**
     * constraints. Key must be in lower case
     */
    private final Map<String, TableListOfColums> constraints = new HashMap<String, TableListOfColums>();

    /**
     * in the BDM, an attribut can be declare as a Collection. Then, in the database, a sub table is
     * created.
     * Key is the complete SQL tablename, like "customer_address" where address is the name of the
     * data model field
     * Key is the table name, in lower case
     */
    private final Map<String, JdbcTable> collectionsTableName = new HashMap<String, JdbcTable>();

    /**
     * all index discover in the database
     */

    public JdbcTable(final JdbcModel jdbcModel, final String tableName, final String sqlOriginaltableName) {
        this.tableName = JdbcTable.getFormatTableName(tableName);
        this.sqlOriginaltableName = sqlOriginaltableName;
        this.jdbcModel = jdbcModel;
    }

    public JdbcTable(final JdbcModel jdbcModel, final String tableName, final String sqlOriginaltableName, final String childTableName) {
        this.tableName = tableName;
        this.sqlOriginaltableName = sqlOriginaltableName;
        this.childTableName = childTableName;
        this.jdbcModel = jdbcModel;
    }

    /**
     * add a new Collection table
     *
     * @param parentTableName is the complete source like "customer"
     * @param childTableName is the name of the child, "address"
     *        The complete tableName is then source parentTableName+"_"+childTableName
     * @param collectionName
     */
    public void addCollectionTable(final JdbcModel jdbcModel, final String parentTableName, final String sqlOriginaltableName, final String childTableName) {
        collectionsTableName.put(
                parentTableName.toLowerCase() + "_" + childTableName.toLowerCase(),
                new JdbcTable(jdbcModel, parentTableName.toLowerCase() + "_" + childTableName.toLowerCase(), sqlOriginaltableName, childTableName));
    }

    public JdbcTable getCollectionTable(final String parentTableName, final String childTableName, final ParametersCalcul parametersCalcul) {
        

        JdbcTable jdbcTable = searchInSet(collectionsTableName, parentTableName, childTableName);
        // let's have a look in the unknow table
        if (jdbcTable == null)
            jdbcTable = searchInSet(jdbcModel.getCollectionUnknowChildTable(), parentTableName, childTableName);
        return jdbcTable;
    }

    /**
     * Parent name is for example "businesscalendar"
     * childtablename is hollydays
     * the collection table is something like businesscalendar_hollydays or businesscal_hollydays or businesscal_hollyd
     * 
     * @param sourceTables
     * @param parentTableName
     * @param childTableName
     * @return
     */
    private JdbcTable searchInSet(Map<String, JdbcTable> sourceTables, String parentTableName, String childTableName) {
        for (String tableName : sourceTables.keySet()) {
            if (tableName.indexOf("_") == -1)
                continue;
            String prefixTable = tableName.substring(0, tableName.indexOf("_"));
            String suffixTable = tableName.substring(tableName.indexOf("_") + 1);
            if (equalsBegining(prefixTable, parentTableName) && equalsBegining(suffixTable, childTableName))
                return sourceTables.get(tableName);
        }

        return null;
    }

    /**
     * @param s1
     * @param s2
     * @return
     */
    public boolean equalsBegining(String s1, String s2) {
        if (s1.length() < s2.length())
            return s1.equalsIgnoreCase(s2.substring(0, s1.length()));
        else
            return s2.equalsIgnoreCase(s1.substring(0, s2.length()));

    }

    /**
     * use the connection to rebuild the datamodel
     *
     * @param con
     * @return
     */

    public String getContentFromConnection(final Connection con, final OperationStatus operationStatus) {
        String result = "";
        ResultSet rs = null;
        try {
            listColumns = new ArrayList<JdbcColumn>();
            boolean oneColumnFound = false;
            final DatabaseMetaData databaseMetaData = con.getMetaData();
            rs = databaseMetaData.getColumns(null /* catalog */, null /* schema */, sqlOriginaltableName, null /*
                                                                                                                * columnNamePattern
                                                                                                                */);
            while (rs.next()) {
                String tableNameCol = rs.getString("TABLE_NAME");
                tableNameCol = tableNameCol == null ? "" : tableNameCol.toLowerCase();

                if (!tableNameCol.equals(tableName)) {
                    continue;
                }
                oneColumnFound = true;

                final JdbcColumn jdbcColumn = new JdbcColumn(this);
                jdbcColumn.colName = rs.getString("COLUMN_NAME");
                jdbcColumn.colName = jdbcColumn.colName == null ? "" : jdbcColumn.colName.toLowerCase();

                jdbcColumn.dataType = rs.getInt("DATA_TYPE");
                jdbcColumn.length = rs.getInt("COLUMN_SIZE");
                if (jdbcColumn.dataType == java.sql.Types.VARCHAR && jdbcColumn.length > 100000) {
                    jdbcColumn.dataType = -333; // special marker for a Text
                }

                // don't keep in mind the system column
                if (jdbcColumn.colName.equals(GeneratorSql.cstColumnPersistenceId)
                        || jdbcColumn.colName.equals(GeneratorSql.cstColumnPersistenceVersion)) {
                    listTechnicalColumns.add( jdbcColumn );
                    
                    continue;
                }
                // this is a reference table : it should appears in the reference
                if (jdbcColumn.colName.endsWith(GeneratorSql.cstSuffixColumnPid)) {
                    // jdbcColumn.colName = jdbcColumn.colName.substring(0, jdbcColumn.colName.length()-4);
                    jdbcColumn.isForeignKey = true;
                }

                if (jdbcColumn.colName.equals("style") || jdbcColumn.colName.equals("string2composition")) {
                    logger.info("JdbcTable : jdbcColumn[" + jdbcColumn.colName + "] type[" + jdbcColumn.dataType + "]");
                }
                jdbcColumn.nullable = "YES".equals(rs.getString("IS_NULLABLE"));
                listColumns.add(jdbcColumn);
            }
            rs.close();

            if (!oneColumnFound) {
                operationStatus.addErrorEvent(new BEvent(EVENT_INCOHERENT_DATABASE, "Table[" + sqlOriginaltableName + "]"));
            }

            // index & constraints
            // i==0 => unique = Constraint
            // i==1 => index

            rs = databaseMetaData.getIndexInfo(null /* String catalog */, null /* String schema */, sqlOriginaltableName, false /** unique */
                    , false /*
                             * boolean
                             * approximate
                             */);
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                indexName = indexName.toLowerCase();
                if (indexName.endsWith("_pkey")) {
                    continue; // this is the primary key
                }
                final String columnName = rs.getString("COLUMN_NAME");
                // String indexQualifier = rs.getString("INDEX_QUALIFIER");
                final boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                // nonUnique = true => index (else constraints)
                TableListOfColums oneListItem = nonUnique ? indexes.get(indexName) : constraints.get(indexName);
                if (oneListItem == null) {
                    oneListItem = new TableListOfColums(true);
                    if (nonUnique) {
                        indexes.put(indexName.toLowerCase(), oneListItem);
                    } else {
                        constraints.put(indexName.toLowerCase(), oneListItem);
                    }

                }
                oneListItem.name = indexName;
                oneListItem.unique = !nonUnique;
                oneListItem.addColumns(columnName);
            }
            rs.close();

            // constraints and reference key
            rs = databaseMetaData.getImportedKeys(null, null, sqlOriginaltableName);

            while (rs.next()) {
                String columnName = rs.getString("FKCOLUMN_NAME");
                columnName = columnName.toLowerCase();
                // search the column
                for (final JdbcColumn jdbcColumn : listColumns) {
                    if (jdbcColumn.colName.equals(columnName)) {
                        jdbcColumn.referenceTable = rs.getString("PKTABLE_NAME");
                        // all table in lower case
                        jdbcColumn.referenceTable = jdbcColumn.referenceTable.toLowerCase();
                        jdbcColumn.contraintsName = rs.getString("FK_NAME");
                    }
                    //   	String pk_column = rs.getString("PKCOLUMN_NAME");
                    //    String constraint_name = rs.getString("FK_NAME");
                }
            }
            rs.close();

            /*
             * ResultSet getCrossReference(String parentCatalog,
             * String parentSchema,
             * String parentTable,
             * String foreignCatalog,
             * String foreignSchema,
             * String foreignTable)
             * throws SQLException
             */

            // now, explore all collectionName
            for (final JdbcTable collectionName : collectionsTableName.values()) {
                result += collectionName.getContentFromConnection(con, operationStatus);
            }

            // let's check all column
            for (final JdbcColumn jdbcColumn : listColumns) {
                if (jdbcColumn.isForeignKey && jdbcColumn.contraintsName == null) {

                    operationStatus.addErrorEvent(new BEvent(EVENT_FOREIGNCONSTRAINTWITHOUTNAME, "Table[" + tableName + "] colum[" + jdbcColumn.colName + "]"));
                    jdbcColumn.isForeignKey = false;
                }
            }
        } catch (final Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionDetails = sw.toString();
            logger.severe("Error " + e.getMessage() + " at " + exceptionDetails);
            operationStatus.addErrorEvent(new BEvent(EVENT_SQLERROR, e, "During analysis of database"));

            result = e.toString();
            if (rs != null) {
                try {
                    rs.close();
                } catch (final SQLException e1) {
                }
            } ;
        }

        return result;

    }

    public String getTableName() {
        return tableName;
    }

    public String getChildTableName() {
        return childTableName;
    }

    public List<JdbcColumn> getListColumns() {
        return listColumns;
    }
    
    /**
     * persistenceId, persistenceVersion are considered as technical columns
     * @return
     */
    public List<JdbcColumn> getListTechnicalColumns() {
        return listTechnicalColumns;
    }
    
    public Map<String, TableListOfColums> getMapIndexes() {
        return indexes;
    }

    public Map<String, TableListOfColums> getMapConstraints() {
        return constraints;
    }

    public Map<String, JdbcTable> getMapTables() {
        return collectionsTableName;
    }

    /**
     * search in the constaint one who define a constraint on this column (expected a foreign key
     * constraint)
     *
     * @return
     */
    public String getContraintsContainsField(final String colName) {
        for (final String name : constraints.keySet()) {
            for (final String colNameInConstraint : constraints.get(name).getListColumns()) {
                // this is the constraint we look for
                if (colNameInConstraint.equals(colName)) {
                    return name;
                }
            }
        }
        return null;
    }

    public JdbcColumn addColName(final String colName) {
        final JdbcColumn jdbcColumn = new JdbcColumn(this);
        jdbcColumn.colName = colName;
        listColumns.add(jdbcColumn);
        return jdbcColumn;

    }

    @Override
    public String toString() {
        return tableName;
    };

    /**
     * Normalise the name, to be sure to compare the same thing in BDM and in DATABASE
     */
    public static String getFormatColName(String colName) {
        return colName.toLowerCase();
    }

    /**
     * use for Constraint and Index name
     * 
     * @param colName
     * @return
     */
    public static String getFormatListNames(String colName) {
        return colName.toLowerCase();
    }

    public static String getFormatTableName(String colName) {
        return colName.toLowerCase();
    }

}

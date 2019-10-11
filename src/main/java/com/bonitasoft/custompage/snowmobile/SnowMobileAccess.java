package com.bonitasoft.custompage.snowmobile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.log.event.BEventFactory;

import java.util.logging.Logger;

import com.bonitasoft.custompage.snowmobile.BdmBusinessObject.BdmListOfFields;
import com.bonitasoft.custompage.snowmobile.JdbcTable.JdbcColumn;
import com.bonitasoft.custompage.snowmobile.JdbcTable.TableListOfColums;
import com.bonitasoft.custompage.snowmobile.OperationStatus.TypeMsg;

public class SnowMobileAccess {

    public static Logger logger = Logger.getLogger(SnowMobileAccess.class.getName());

    private static final BEvent EVENT_CONSTRAINT_CREATE = new BEvent(SnowMobileAccess.class.getName(), 1, Level.INFO, "New Contraint detected", "A contraint, present in the BDM model does not exist in the database", "", "This constraint must be created");
    private static final BEvent EVENT_CONSTRAINT_DISEAPEAR = new BEvent(SnowMobileAccess.class.getName(), 2, Level.INFO, "Contraint diseapear", "A contraint, present in the database, is not defined in the new BDM model", "", "This constraint must be deleted");
    private static final BEvent EVENT_CONSTRAINT_CHANGE = new BEvent(SnowMobileAccess.class.getName(), 3, Level.INFO, "Contraint change", "A constraint, present in the database, change in the new BDM model", "", "This constraint must change");

    private static final BEvent EVENT_INDEX_CREATE = new BEvent(SnowMobileAccess.class.getName(), 4, Level.INFO, "New Index detected", "A index, defined in the BDM model does not exist in the database", "", "This index must be created");
    private static final BEvent EVENT_INDEX_DISEAPEAR = new BEvent(SnowMobileAccess.class.getName(), 5, Level.INFO, "Index diseapear", "A index, present in the database, is not defined in the new BDM model", "", "This index must be deleted");
    private static final BEvent EVENT_INDEX_CHANGE = new BEvent(SnowMobileAccess.class.getName(), 6, Level.INFO, "Index change", "A index, present in the database, change in the new BDM model", "", "This index must change");

    private static final BEvent EVENT_TABLE_CREATE = new BEvent(SnowMobileAccess.class.getName(), 7, Level.INFO, "New Table detected", "A table, present in the BDM model does not exist in the database", "", "This table must be created");
    private static final BEvent EVENT_TABLE_DISEAPEAR = new BEvent(SnowMobileAccess.class.getName(), 8, Level.INFO, "Table diseapear", "A Table, present in the database, is not defined in the new BDM Model", "", "The Table must be deleted");

    private static final BEvent EVENT_FIELD_TO_COLLECTION = new BEvent(SnowMobileAccess.class.getName(), 9, Level.INFO, "Field to collection", "A field changed: it is now multiple in the BDM model", "", "A new table, to contains the list of values, is created and associated to the field");
    private static final BEvent EVENT_FIELD_NEW_COLLECTION = new BEvent(SnowMobileAccess.class.getName(), 10, Level.INFO, "New Field collection", "A new field, multiple in the BDM model, is created", "", "A new table, to contains the list of values, is created and associated to the field");
    private static final BEvent EVENT_FIELD_CREATE = new BEvent(SnowMobileAccess.class.getName(), 11, Level.INFO, "New Field detected", "A new field, is created", "", "A new column is created");
    private static final BEvent EVENT_FIELD_CHANGE = new BEvent(SnowMobileAccess.class.getName(), 12, Level.INFO, "Field change", "A existing field change", "", "A column has to change");
    private static final BEvent EVENT_FIELD_DISEAPEAR = new BEvent(SnowMobileAccess.class.getName(), 13, Level.INFO, "Field diseapear", "A field, present in the database, is not defined in the new BDM Model", "", "The field must be deleted");

    private static final BEvent EVENT_COMPOSITION_ADD = new BEvent(SnowMobileAccess.class.getName(), 14, Level.INFO, "New composition detected", "A new composition is created", "", "A new composition must be created");
    private static final BEvent EVENT_COMPOSITION_ADD_ORDER = new BEvent(SnowMobileAccess.class.getName(), 15, Level.INFO, "New composition order", "A new composition order is created", "", "A new field to save the order must be created");
    private static final BEvent EVENT_COMPOSITION_DROP = new BEvent(SnowMobileAccess.class.getName(), 16, Level.INFO, "Composition diseapear", "A composition, present in the database, is not defined in the new BDM Model", "", "The composition must be deleted");

    private static final BEvent EVENT_COLLECTION_DISEAPEAR = new BEvent(SnowMobileAccess.class.getName(), 17, Level.INFO, "Collection diseapear", "A collection, present in the database, is not defined in the new BDM Model", "", "The collection must be deleted");


    // errors events
    private static BEvent EVENT_FILEMISSING = new BEvent(SnowMobileAccess.class.getName(), 18, Level.APPLICATIONERROR,
            "Give a file", "Give a file to do the differential", "No differential can be calculated", "Upload a BDM.zip file");
    private static BEvent EVENT_NO_DRIVER = new BEvent(SnowMobileAccess.class.getName(), 19, Level.APPLICATIONERROR,
            "No Javaclass for the driver", "Database return a driver name. No Java class is found with that name", "No differential can be calculated", "Put the Driver name in <Tomcat>/lib");
    private static BEvent EVENT_META_MODEL_FAILED = new BEvent(SnowMobileAccess.class.getName(), 20, Level.APPLICATIONERROR,
            "Meta model reading failed", "The MetaModel of the database can't be read", "No differential can be calculated", "Check the database");
    private static BEvent EVENT_READBDMZIP = new BEvent(SnowMobileAccess.class.getName(), 21, Level.APPLICATIONERROR,
            "Zip file can't be open", "The file can't be open", "No differential can be calculated", "Check file you uploaded");
    private static BEvent EVENT_BOMNOTFOUNDINZIP = new BEvent(SnowMobileAccess.class.getName(), 22, Level.APPLICATIONERROR,
            "Incoherent Zip file", "The BDM Zip file must contains a file name [bom.xml]", "No differential can be calculated", "Check file you uploaded");
    private static BEvent EVENT_READINGZIPFILE = new BEvent(SnowMobileAccess.class.getName(), 23, Level.APPLICATIONERROR,
            "Error in Zip file", "The ZIP file can't be read", "No differential can be calculated", "Check file you uploaded");

    
    private BdmContent bdmContent;
    private JdbcModel jdbcModel;

    public static class ParametersCalcul {

        public boolean commentDropTable = false;
        public boolean commentDropColumn = false;
        public boolean commentDropIndex = false;
        public boolean commentDropConstraint = false;
        public boolean commentExtraDropTables = false;
        public boolean calculChangeTypeColumn = true;
        public boolean addBusinessComment = true;
        /**
         * the subcollection table is limited to 14 char.
         * Then, when the table name is "ThisIsATableNameVeryLong", and this table name has a collection
         * field like "mainaddress" which is a collection, then
         * the table name is
         * ThisIsATableNa_mainaddress
         */
        public int maxNumberOfCharacterForCollectionTable = 14;

    }

    private File pageDirectory = null;

    /**
     * @param args
     */
    public HashMap<String, Object> calcul(final String bdmFileNameZip, final ParametersCalcul parametersCalcul) {
        final HashMap<String, Object> result = new HashMap<String, Object>();

        final SnowMobileAccess sqlUpdate = new SnowMobileAccess();

        final OperationStatus operationResult = new OperationStatus();
        sqlUpdate.setBdmFromFile(bdmFileNameZip, operationResult);
        if (operationResult.isError()) {
            result.put("msg", operationResult.getMsg());
            result.put("errormsg", BEventFactory.getHtml(operationResult.getErrors()));
            return result;
        }

        // set the datamodele

        // calcul
        sqlUpdate.calculSqlScript(parametersCalcul, operationResult);
        result.put("sql", operationResult.getSql());
        result.put("msg", operationResult.getMsg());
        result.put("errormsg", BEventFactory.getHtml(operationResult.getErrors()));

        return result;

    }

    public void setContext(final APISession session, File pageDirectory, final ProcessAPI processAPI) {
        this.pageDirectory = pageDirectory;

        logger.info("PageDirectory [" + pageDirectory + "]");
    }

    
    /**
     * set the BDM from the bdm zip file
     *
     * @param bdmFileNameZip
     * @return
     */
    

    
    public void setBdmFromFile(final String bdmFileNameZip, final OperationStatus operationStatus) {
        if (bdmFileNameZip == null || bdmFileNameZip.length() == 0) {
            operationStatus.addErrorEvent( EVENT_FILEMISSING );
            return;
        }

        File completeBdmFileNameZip = null;

        List<String> listParentTmpFile = new ArrayList<String>();
        try {
            listParentTmpFile.add(pageDirectory.getCanonicalPath() + "/../../../tmp/");
            listParentTmpFile.add(pageDirectory.getCanonicalPath() + "/../../");
        } catch (Exception e) {
            logger.info("SnowMobileAccess : error get CanonicalPath of pageDirectory[" + e.toString() + "]");
            return;
        }
        for (String pathTemp : listParentTmpFile) {
            logger.info("SnowMobileAccess : CompleteuploadFile  TEST [" + pathTemp + bdmFileNameZip + "]");
            if (bdmFileNameZip.length() > 0 && (new File(pathTemp + bdmFileNameZip)).exists()) {
                completeBdmFileNameZip = (new File(pathTemp + bdmFileNameZip)).getAbsoluteFile();
                logger.info("SnowMobileAccess : CompleteuploadFile  FOUND [" + completeBdmFileNameZip + "]");
            }
        }

        if (!completeBdmFileNameZip.exists()) {
            operationStatus.addErrorEvent( new BEvent(EVENT_READBDMZIP, "file [" + bdmFileNameZip + "] can't be open as it or with [" + completeBdmFileNameZip + "]"));
            return;
        }
     
        boolean foundOneFile = false;
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(completeBdmFileNameZip));

            // get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                if (ze.getName().equals("bom.xml")) {
                    final byte[] buffer = new byte[1024];

                    final ByteArrayOutputStream bosBuffer = new ByteArrayOutputStream();
                    int len = 0;
                    while ((len = zis.read(buffer)) > 0) {
                        bosBuffer.write(buffer, 0, len);
                    }
                    foundOneFile = true;
                    bdmContent = new BdmContent();
                    bdmContent.readFromXml(bosBuffer.toString(), operationStatus);
                }
                ze = zis.getNextEntry();
            }
            zis.close();
           
            if (!foundOneFile) {
                operationStatus.addErrorEvent(EVENT_BOMNOTFOUNDINZIP);
            }
        } catch (final IOException ie) {
            operationStatus.addErrorEvent( new BEvent( EVENT_READINGZIPFILE, "File Reading Error :" + ie));
        } catch (final Exception e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            operationStatus.addErrorEvent( new BEvent(EVENT_READINGZIPFILE, "Zip failed :" + e.getMessage()+" at "+ exceptionDetails));
        } finally {
            if (zis != null) {
                try {
                    zis.close();
                } catch (final IOException e1) {
                }
            }

        }
        return;
    }

    /**
     * return the bdm object
     *
     * @return
     */
    public BdmContent getBdm() {
        return bdmContent;
    }

    /**
     * set the Datamodel from the Datasource name
     *
     * @param databasesourceName
     * @return
     */
    public void setDatamodelFromDatabaseSource(final String databasesourceName, final OperationStatus operationStatus) {
        // try to add java:/comp/env/
        // TOMCAT : java:/comp/env/bonitaSequenceManagerDS
        // JBOSS : java:jboss/datasources/bonitaSequenceManagerDS
        DataSource ds = null;
        String differentTests = "";

        Connection con = null;
        try {
            con = getConnection();

            if (con == null) {
                operationStatus.addMsg("Can't connect Datasource");
                return;
            }
            jdbcModel = new JdbcModel();
            jdbcModel.readFromConnection(con, operationStatus);
            operationStatus.addMsg("Datasource [" + databasesourceName + "] Connected with success;");
        } catch (final Exception e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            
            operationStatus.addErrorEvent( new BEvent(EVENT_META_MODEL_FAILED, e.getMessage()+" at " + exceptionDetails));
        } finally {
            if (con != null)
                try {
                    con.close();
                } catch (Exception e) {
                } ;
        }
        return;

    }

    /**
     * get the metamodel from a connection, given drivername, connectstring and
     * user/password
     *
     * @param jdbcDriverName
     * @param connectString
     * @param userName
     * @param passwd
     * @return
     */

    public void setDatamodelFromDatabaseConnection(final String jdbcDriverName, final String connectString, final String userName, final String passwd, final OperationStatus operationStatus) {
        Connection jdbcConnection = null;
        try {
            final Class<?> driverClass = Class.forName(jdbcDriverName);
            if (driverClass == null) {
                operationStatus.addErrorEvent( new BEvent(EVENT_NO_DRIVER, "driverName[" + jdbcDriverName + "]"));
                return;
            }

            jdbcConnection = DriverManager.getConnection(connectString, userName, passwd);
            jdbcModel = new JdbcModel();
            jdbcModel.readFromConnection(jdbcConnection, operationStatus);
        } catch (final Exception e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            operationStatus.addErrorEvent( new BEvent(EVENT_META_MODEL_FAILED, e.getMessage()+" at " + exceptionDetails));
        } finally {
            if (jdbcConnection != null) {
                try {
                    jdbcConnection.close();
                } catch (final SQLException e1) {
                }
            } ;

        }
        return;
    }

    /* ******************************************************************************** */
    /*                                                                                  */
    /* Calcul */
    /*                                                                                  */
    /*                                                                                  */
    /* ******************************************************************************** */
    /**
     * calcul the script between the bdm file and the database
     *
     * @return
     */
    public void calculSqlScript(final ParametersCalcul parametersCalcul, final OperationStatus operationStatus) {
        final GeneratorSql generatorSql = new GeneratorSql(parametersCalcul, bdmContent, jdbcModel, operationStatus);
        final Map<String, JdbcTable> mapMetaModelTable = jdbcModel.getSetTables();

        operationStatus.addDeltaMsg(null, null, "Database : " + jdbcModel.getDatabaseProductName(), TypeMsg.INFO);
        operationStatus.addSqlPreUpdate("-- Database : " + jdbcModel.getDatabaseProductName());

        // -------------------- First pass : generate all table. This is
        // necessary for the composition table
        for (final BdmBusinessObject bdmBusinessObject : bdmContent.getListBdmBusinessObject().values()) {
            if (parametersCalcul.addBusinessComment) {
                operationStatus.addBusinessObject("Business Object " + bdmBusinessObject.getLogId());
            } else {
                operationStatus.addConditionalHeader("Business Object " + bdmBusinessObject.getLogId());
            }

            operationStatus.addSqlConditionalHeader("Business Object " + bdmBusinessObject.getLogId());
            // --------------------- table
            final JdbcTable jdbcTable = jdbcModel.getTable(bdmBusinessObject.getSqlTableName());
            if (jdbcTable == null) {
                // the table does not exist : generate it !
                operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_TABLE_CREATE, "Table[" + bdmBusinessObject.getSqlTableName() + "]"), TypeMsg.ADD);

                generatorSql.sqlGenerateTable(bdmBusinessObject);

                // add the table now !
                /*
                 * jdbcTable = new JdbcTable(bdmBusinessObject.tableName );
                 * mapMetaModelTable.put( bdmBusinessObject.tableName, jdbcTable
                 * ); for (BdmField bdmField : bdmBusinessObject.listFields) {
                 * jdbcTable.addColName( bdmField.getSqlColName()); }
                 */

            }
        }

        // --------------------- second pass : run all BDM and manage the field
        // (but not the composition)
        for (final BdmBusinessObject bdmBusinessObject : bdmContent.getListBdmBusinessObject().values()) {
            if (parametersCalcul.addBusinessComment) {
                operationStatus.addBusinessObject("Business Object " + bdmBusinessObject.getLogId());
            } else {
                operationStatus.addConditionalHeader("Business Object " + bdmBusinessObject.getLogId());
            }

            operationStatus.addSqlConditionalHeader("Business Object " + bdmBusinessObject.getLogId());
            // --------------------- table
            final JdbcTable jdbcTable = mapMetaModelTable.get(bdmBusinessObject.getSqlTableName());
            if (jdbcTable == null) {
                // the table does not exist : already managed at first pass, all
                // fields are created.
                continue;
            }

            // check each column now

            final List<JdbcColumn> listColumns = jdbcTable.getListColumns();
            final HashMap<String, JdbcColumn> mapColums = new HashMap<String, JdbcColumn>();
            // collect each SqlColum from the Bdm field
            final HashMap<String, BdmField> mapBdmFieldColumn = new HashMap<String, BdmField>();
            for (final JdbcColumn oneColumn : listColumns) {
                mapColums.put(oneColumn.getColName(), oneColumn);
            }

            for (final BdmField bdmField : bdmBusinessObject.getListFields()) {

                final JdbcColumn jdbcColumn = mapColums.get(bdmField.getSqlColName());
                // a relation COMPOSITION has two cases :
                // - A COLLECTION : nothing in the table, all in the external
                // table (<table>_pid + <field>_order>)
                // - NOT A COLLECTION : one file is created here
                // <compositionname>_pid + constraints

                // collect the name
                mapBdmFieldColumn.put(bdmField.getSqlColName(), bdmField);
                // ----------------------------------------- Collection
                if (bdmField.isCollection()) {
                    if (bdmField.isComposition()) {
                        // create the composition in the sub table : will be
                        // done after at the Composition step
                        if (jdbcColumn != null) {
                            // we should add a script to update the composition
                            // table : we go from a relation 1-1 to 1-n but at
                            // this moment, the composition table is not created
                            // AND what is the script ?
                            // Single -> Collection : remove the field
                            generatorSql
                                    .addComment("A composition field move to a composition collection : may be an update is necessary to not loose your data");
                            if (jdbcColumn.contraintsName != null) {
                                generatorSql.sqlDropConstraint(bdmBusinessObject.getSqlTableName(), jdbcColumn.contraintsName);
                            }
                            generatorSql.sqlDropColumn(bdmBusinessObject.getSqlTableName(), bdmField.getSqlColName(), parametersCalcul.commentDropColumn);
                        }

                        continue;
                    } else if (jdbcTable.getCollectionTable(bdmBusinessObject.getSqlTableName(), bdmField.getName(), parametersCalcul) != null) {
                        operationStatus.addMsg(bdmField.getLogId() + ":IDENTICAL,");
                        continue;
                    } else {
                        // collection not exist
                        // two case : it maybe a field which change to a
                        // collection
                        if (jdbcColumn != null) {
                            operationStatus.addDeltaEvent(bdmBusinessObject, bdmField, new BEvent(EVENT_FIELD_TO_COLLECTION, "Field [" + bdmField.getName() + "]"), TypeMsg.ALTER);
                            generatorSql.sqlColumnToCollection(bdmField);
                        } else {
                            operationStatus.addDeltaEvent(bdmBusinessObject, bdmField, new BEvent(EVENT_FIELD_NEW_COLLECTION, "Field [" + bdmField.getName() + "]"), TypeMsg.ADD);

                            generatorSql.sqlCreateCollection(bdmField);
                        }
                        continue;
                    }
                }
                // ------------------------------------ simple field
                // (composition or not)
                if (jdbcColumn == null) {
                    String label = "New simple field";
                    if (bdmField.isAggregation()) {
                        label = "New aggregation field";
                    }
                    if (bdmField.isComposition()) {
                        label = "New composition field";
                    }

                    operationStatus.addDeltaEvent(bdmBusinessObject, bdmField, new BEvent(EVENT_FIELD_CREATE, label + ":" + bdmField.getName() + " (" + bdmField.fieldType + ")"), TypeMsg.ADD);

                    generatorSql.sqlCreateColumn(bdmField, false);
                    // attention : this is maybe a OLD collection ? This is
                    // managed after.
                    continue;
                }

                final long fieldComparaison = compareFields(bdmField, jdbcColumn, generatorSql);
                final String headerMsg = bdmField.getLogId() + ":";

                if ((fieldComparaison & EnumFieldComparaisonIDENTICAL) != 0) {
                    operationStatus.addMsg(headerMsg + "IDENTICAL,");
                    continue;
                }

                String deltaMsg = null;
                // field may change on some attributes
                if ((fieldComparaison & EnumFieldComparaisonBDMISNULLABLE) != 0) {
                    deltaMsg = headerMsg + "Bdm is Nullable";
                    generatorSql.sqlColumnToNullable(bdmField);
                }
                if ((fieldComparaison & EnumFieldComparaisonJDBCISNULLABLE) != 0) {
                    deltaMsg = "Jdbc is Nullable";
                    generatorSql.sqlColumnToNotNullable(bdmField);
                }
                if ((fieldComparaison & EnumFieldComparaisonLENGTHCHANGE) != 0) {
                    deltaMsg = "Length change [" + jdbcColumn.length + "] -> [" + bdmField.fieldLength + "]";
                    generatorSql.sqlColumnLengthChange(bdmField);
                }
                if ((fieldComparaison & EnumFieldComparaisonTYPECHANGE) != 0) {
                    deltaMsg = "Type change [" + generatorSql.getSqlType(jdbcColumn) + "]->[" + generatorSql.getSqlType(bdmField) + "]";
                    generatorSql.sqlColumnTypeChange(bdmField);
                }
                if ((fieldComparaison & EnumFieldComparaisonBDMISAFOREIGNKEY) != 0) {
                    deltaMsg = "Bdm is Foreign";
                    generatorSql.sqlIsForeignKey(bdmField);
                }

                if ((fieldComparaison & EnumFieldComparaisonJDBCISAFOREIGNKEY) != 0) {
                    deltaMsg = "Jdbc is Foreign";
                    generatorSql.sqlIsNotForeignKey(bdmField, jdbcColumn);
                }
                if ((fieldComparaison & EnumFieldComparaisonFOREIGNTABLECHANGE) != 0) {
                    deltaMsg = "Foreign table change";
                    // drop the current key
                    generatorSql.sqlDropForeignConstraint(bdmField, jdbcColumn.contraintsName);
                    generatorSql.addComment("The column " + bdmField.getSqlCompleteColName() + " should be purge because the constrainst change");
                    generatorSql.sqlCreateForeignConstraint(bdmField, false);
                }
                if (deltaMsg != null) {
                    operationStatus.addDeltaEvent(bdmBusinessObject, bdmField, new BEvent(EVENT_FIELD_CHANGE, headerMsg + deltaMsg), TypeMsg.ALTER);
                } else {
                    operationStatus.addMsg(headerMsg + "identical");
                }

            } // End check each field

            // complete the mapBdmFieldColumn by FATHER object which reference this object : then some field are added
            for (final BdmBusinessObject bdmBusinessObjectFather : bdmBusinessObject.getBusinessFather().values()) {

                // Order is the fieldRelation name : fieldRelation_order. So, found each field related to this table ONLY IF THE FATHER FIELD is a COLLECTION
                for (final BdmField bdmField : bdmBusinessObjectFather.getListFields()) {
                    if (bdmBusinessObject.getName().equals(bdmField.getReference()) && bdmField.isCollection()) {
                        // pid is the father (FatherTableName_pid)
                        mapBdmFieldColumn.put(bdmBusinessObjectFather.getSqlTableName() + GeneratorSql.cstSuffixColumnPid, bdmField);
                        // the order is the name of the father column name
                        mapBdmFieldColumn.put(bdmField.getFieldName().toLowerCase() + GeneratorSql.cstSuffixColumnOrder, bdmField);
                    }
                }
            }

            // delete all non use fields
            /*
             * do after the checkConstraints
             * for (final JdbcColumn jdbcColumn : jdbcTable.getListColumns())
             * {
             * // finish by _pid or -order ? Will be manage after in the composition stuff
             * if (!mapBdmFieldColumn.containsKey(jdbcColumn.colName)
             * && !jdbcColumn.colName.endsWith(GeneratorSql.cstSuffixColumnPid)
             * && !jdbcColumn.colName.endsWith(GeneratorSql.cstSuffixColumnOrder)) {
             * generatorSql.sqlDropColumn(jdbcTable.getTableName(), jdbcColumn.colName, true);
             * }
             * }
             */
        }

        // ----------------------------- third pass : the Composition
        for (final BdmBusinessObject bdmBusinessObject : bdmContent.getListBdmBusinessObject().values()) {
            if (parametersCalcul.addBusinessComment) {
                operationStatus.addBusinessObject("Composition Pass : Business Object " + bdmBusinessObject.getLogId());
            } else {
                operationStatus.addConditionalHeader("Composition Pass : Business Object " + bdmBusinessObject.getLogId());
            }

            operationStatus.addSqlConditionalHeader("Composition Pass : Business Object " + bdmBusinessObject.getLogId());

            final JdbcTable jdbcTable = mapMetaModelTable.get(bdmBusinessObject.getSqlTableName());
            // if the table is NEW then there are no table. And then the
            // composition is not made.

            final List<JdbcColumn> listColumns = jdbcTable == null ? new ArrayList<JdbcColumn>() : jdbcTable.getListColumns();
            final HashMap<String, JdbcColumn> mapColums = new HashMap<String, JdbcColumn>();
            for (final JdbcColumn oneColumn : listColumns) {
                mapColums.put(oneColumn.getColName(), oneColumn);
            }

            // ------------------------------ Composition of THIS BDM : DBM to
            // his father
            // One business Object is related to my father as a
            // relation="COMPOSITION" and COLLECTION=TRUE"
            // Check my father : I'm still referenced in this configuration ? If
            // yes, add two fields per father collection. If no, purge this 2
            // fields
            // order field : ths issue is there are no direct relation between
            // _order and the parent. Order fields name are FATHER :
            // Header.linestovisit => orderLine.linestovisit_order
            // then, to purge the _order field, we have to look all father. If
            // the BDM is referenced as a COLLECTION (whatever the relation -
            // composition or not) then the _order must be kept
            final HashSet<String> mapBdmIsReferencedInFather = new HashSet<String>();
            for (final BdmBusinessObject bdmBusinessObjectFather : bdmBusinessObject.getBusinessFather().values()) {

                final BdmField referenceBdmFieldFather = bdmBusinessObjectFather.getFieldReference(bdmBusinessObject.getName());
                if (referenceBdmFieldFather != null && referenceBdmFieldFather.isCollection()) {
                    String colNameOrder = referenceBdmFieldFather.getSqlColName();
                    if (colNameOrder.endsWith(GeneratorSql.cstSuffixColumnPid)) {
                        colNameOrder = colNameOrder.substring(0, colNameOrder.length() - 4);
                    }
                    colNameOrder += GeneratorSql.cstSuffixColumnOrder;
                    mapBdmIsReferencedInFather.add(colNameOrder);
                }

                if (referenceBdmFieldFather != null && referenceBdmFieldFather.isComposition() && referenceBdmFieldFather.isCollection()) {
                    // ok, I'm referenced in a father, as a composition AND as a
                    // collection => composition field are present

                    //
                    if (mapColums.get(bdmBusinessObjectFather.getSqlTableName() + GeneratorSql.cstSuffixColumnPid) == null) {
                        String deltaMsg = "Add COMPOSITION from[" + bdmBusinessObjectFather.getName() + "]";
                        final BdmField bdmField = new BdmField(bdmBusinessObject);
                        bdmField.setName(bdmBusinessObjectFather.getSqlTableName());
                        bdmField.fieldType = "LONG";
                        bdmField.nullable = false;
                        bdmField.isRelationField = true; // so the _pid will be
                                                         // add, and the foreign
                                                         // key too
                        bdmField.relationType = bdmField.relationType.AGGREGATION; // let's
                                                                                   // say
                                                                                   // it's
                                                                                   // a
                                                                                   // agregation
                                                                                   // :
                                                                                   // the
                                                                                   // constraint
                                                                                   // is
                                                                                   // then
                                                                                   // created
                        bdmField.referenceSqlTable = bdmBusinessObjectFather.getSqlTableName();

                        operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_COMPOSITION_ADD, deltaMsg), TypeMsg.ADD);

                        generatorSql.sqlCreateColumn(bdmField, false);
                    }
                    // the order has the same name as the table...
                    String colNameOrder = referenceBdmFieldFather.getSqlColName();
                    if (colNameOrder.endsWith(GeneratorSql.cstSuffixColumnPid)) {
                        colNameOrder = colNameOrder.substring(0, colNameOrder.length() - 4);
                    }
                    colNameOrder += GeneratorSql.cstSuffixColumnOrder;
                    if (mapColums.get(colNameOrder) == null) {
                        String deltaMsg = ", Add COMPO_order from[" + bdmBusinessObjectFather.getName() + "]";
                        final BdmField bdmField = new BdmField(bdmBusinessObject);
                        bdmField.setName(colNameOrder);
                        bdmField.fieldType = "INTEGER";
                        bdmField.nullable = true;
                        operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_COMPOSITION_ADD_ORDER, deltaMsg), TypeMsg.ADD);

                        generatorSql.sqlCreateColumn(bdmField, false);
                        // reference this new _order fields please
                        mapBdmIsReferencedInFather.add(colNameOrder);

                    }
                } else if (referenceBdmFieldFather != null && referenceBdmFieldFather.isComposition() && !referenceBdmFieldFather.isCollection()
                        || referenceBdmFieldFather == null) {
                    // opposite, I'm referenced in the father, as a composition
                    // BUT not a composition OR I'm not referenced anymore tin
                    // the parent ==> remove the collection composition field
                    if (mapColums.get(bdmBusinessObjectFather.getSqlTableName() + GeneratorSql.cstSuffixColumnPid) != null) {
                        final JdbcColumn jdbcColum = mapColums.get(bdmBusinessObjectFather.getSqlTableName() + GeneratorSql.cstSuffixColumnPid);
                        String deltaMsg = "Remove COMPOSITION_pid from[" + bdmBusinessObjectFather.getName() + "]";
                        // find the constraint on the field
                        // bdmBusinessObjectFather.getSqlTableName()+"_pid"
                        operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_COMPOSITION_DROP, deltaMsg), TypeMsg.DROP);

                        if (jdbcColum.contraintsName != null) {
                            generatorSql.sqlDropConstraint(jdbcColum.getJdbcTable().getTableName(), jdbcColum.contraintsName);
                        }
                        generatorSql.sqlDropColumn(jdbcColum.getJdbcTable().getTableName(), jdbcColum.getColName(), false);
                    }
                    // Case COorder is the name of the field ON the father table
                    // ( ! ) MINUS _pid

                }

            }
            // ok, last operation, purge all _order field now
            for (final JdbcColumn jdbcColumn : mapColums.values()) {
                if (jdbcColumn.getColName().endsWith(GeneratorSql.cstSuffixColumnOrder) && !mapBdmIsReferencedInFather.contains(jdbcColumn.getColName())) {
                    generatorSql.addComment("Remove  <COMPOSITION>_order from[" + bdmBusinessObject.getName() + "]");
                    generatorSql.sqlDropColumn(bdmBusinessObject.getSqlTableName(), jdbcColumn.getColName(), false);
                }

            }

            // collections : all new collection is manage in the previous loop.
            // Now, what's about the deleted SUB collection (the BDM reference a
            // field name as collection (and not a composition - collection :
            // the composition are not in the getMapTables() )
            if (jdbcTable != null) {
                for (final JdbcTable jdbcTableCollection : jdbcTable.getMapTables().values()) {

                    // the table name is businessData_ubname
                    // like "customer_adress", so we have to extract the sub
                    // field name
                    final BdmField bdmField = bdmBusinessObject.getFieldBySqlColumnName(jdbcTableCollection.getChildTableName());
                    if (bdmField == null || !bdmField.isCollection()) {
                        operationStatus.addDeltaEvent(bdmBusinessObject, bdmField, new BEvent(EVENT_COLLECTION_DISEAPEAR, "Drop collection [" + jdbcTableCollection.getChildTableName() + "]"), TypeMsg.DROP);
                        generatorSql.sqlDropTable(jdbcTableCollection.getTableName());
                    }
                }
            }

            // check indexes
            if (jdbcTable != null) {
                compareIndexOrConstraints(bdmBusinessObject, bdmBusinessObject.getListIndexes(), jdbcTable.getMapIndexes(), true, generatorSql, operationStatus);
            }

            // check constraints
            if (jdbcTable != null) {
                compareIndexOrConstraints(bdmBusinessObject, bdmBusinessObject.getListConstraints(), jdbcTable.getMapConstraints(), false, generatorSql,
                        operationStatus);
            }

            // is some field does not exist in the Bdm ?
            // add all the fields from the COMPOSITION
            final HashSet<String> listOfCompositionField = new HashSet<String>();
            // FatherTable.fieldRelation ==> Child
            for (final BdmBusinessObject bdmBusinessObjectFather : bdmBusinessObject.getBusinessFather().values()) {

                // Order is the fieldRelation name : fieldRelation_order. So, found each field related to this table ONLY IF THE FATHER FIELD is a COLLECTION
                for (final BdmField bdmField : bdmBusinessObjectFather.getListFields()) {
                    if (bdmBusinessObject.getName().equals(bdmField.getReference()) && bdmField.isCollection()) {
                        // pid is the father (FatherTableName_pid)
                        listOfCompositionField.add(bdmBusinessObjectFather.getSqlTableName() + GeneratorSql.cstSuffixColumnPid);
                        // the order is the name of the father column name
                        listOfCompositionField.add(bdmField.getFieldName().toLowerCase() + GeneratorSql.cstSuffixColumnOrder);
                    }
                }

            }

            // check fields in the BDM, not anymore used
            if (jdbcTable != null) {
                for (final JdbcColumn jdbcColumn : jdbcTable.getListColumns()) {
                    if (bdmBusinessObject.getFieldBySqlColumnName(jdbcColumn.getColName()) == null && !listOfCompositionField.contains(jdbcColumn.getColName())) {
                        operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_FIELD_DISEAPEAR, "Drop column [" + jdbcColumn.getColName() + "]"), TypeMsg.DROP);
                        if (jdbcColumn.contraintsName != null) {
                            generatorSql.sqlDropConstraint(jdbcTable.getTableName(), jdbcColumn.contraintsName);
                        }
                        generatorSql.sqlDropColumn(jdbcTable.getTableName(), jdbcColumn.getColName(), parametersCalcul.commentDropColumn);
                    }
                }
            }
        } // end for bdmBusinessObject

        // Now delete all non needed table
        // calcul all table
        final HashSet<String> setTableFromModel = new HashSet<String>();
        for (final BdmBusinessObject bdmBusinessObject : bdmContent.getListBdmBusinessObject().values()) {
            setTableFromModel.add(bdmBusinessObject.getSqlTableName());
            // search all collection
            for (final BdmField bdmField : bdmBusinessObject.listFields) {
                if (bdmField.collection) {
                    setTableFromModel.add(generatorSql.getCollectionTableName(bdmField));
                }
            }
        }

        // build the list
        final HashSet<String> listTables = new HashSet<String>();
        for (final JdbcTable jdbcTable : jdbcModel.getSetTables().values()) {
            listTables.add(jdbcTable.getTableName());
            for (final JdbcTable jdbcSubTable : jdbcTable.getMapTables().values()) {
                listTables.add(jdbcSubTable.getTableName());
            }
        }

        // now check
        for (final String tableName : listTables) {
            // already deleted by a collection ?
            if (generatorSql.getDropTables().contains(tableName)) {
                continue;
            }

            if (!setTableFromModel.contains(tableName)) {
                operationStatus.addDeltaEvent(null, null, new BEvent(EVENT_TABLE_DISEAPEAR, "Drop table[" + tableName + "]"), TypeMsg.DROP);

                generatorSql.sqlDropTable(tableName, parametersCalcul.commentExtraDropTables);
            }
        }

        return;
    }

    /* ******************************************************************************** */
    /*                                                                                  */
    /* Compare methods */
    /*                                                                                  */
    /*                                                                                  */
    /* ******************************************************************************** */
    /**
     * different change on a colum. It's not an enum because multiple change can
     * apply : DBMISNULLABLE and LENGTH for example
     */
    public int EnumFieldComparaisonIDENTICAL = 1;
    public int EnumFieldComparaisonBDMISNULLABLE = 2;
    public int EnumFieldComparaisonJDBCISNULLABLE = 4;
    public int EnumFieldComparaisonLENGTHCHANGE = 8;
    public int EnumFieldComparaisonTYPECHANGE = 16;
    public int EnumFieldComparaisonBDMISAFOREIGNKEY = 32;
    public int EnumFieldComparaisonJDBCISAFOREIGNKEY = 64;
    public int EnumFieldComparaisonFOREIGNTABLECHANGE = 128;

    /**
     * compare the BdmField and the JdbcColumn , and give all differences. The
     * long returned is a bit mask information of all the difference constant.
     *
     * @param bdmField
     * @param jdbcColumn
     * @param generatorSql
     * @return
     */
    private long compareFields(final BdmField bdmField, final JdbcColumn jdbcColumn, final GeneratorSql generatorSql) {
        // keep in mind the name is correct
        long currentComparaison = 0;
        if (bdmField.nullable && !jdbcColumn.nullable) {
            currentComparaison |= EnumFieldComparaisonBDMISNULLABLE;
        } else if (!bdmField.nullable && jdbcColumn.nullable) {
            currentComparaison |= EnumFieldComparaisonJDBCISNULLABLE;
        }

        if ("STRING".equals(bdmField.fieldType)) {
            if (bdmField.fieldLength != jdbcColumn.length) {
                currentComparaison |= EnumFieldComparaisonLENGTHCHANGE;
            }
        }

        if (!generatorSql.getSqlType(bdmField).equals(generatorSql.getSqlType(jdbcColumn))) {
            currentComparaison |= EnumFieldComparaisonTYPECHANGE;
        }

        if (bdmField.isRelationField && !jdbcColumn.isForeignKey) {
            currentComparaison |= EnumFieldComparaisonBDMISAFOREIGNKEY;
        }
        if (!bdmField.isRelationField && jdbcColumn.isForeignKey) {
            currentComparaison |= EnumFieldComparaisonJDBCISAFOREIGNKEY;
        }

        if (bdmField.isRelationField && jdbcColumn.isForeignKey) {
            // same foreign table ?
            if (!bdmField.referenceSqlTable.equals(jdbcColumn.referenceTable)) {
                currentComparaison |= EnumFieldComparaisonFOREIGNTABLECHANGE;
            }

        }

        return currentComparaison == 0 ? EnumFieldComparaisonIDENTICAL : currentComparaison;
    }

    /**
     * @param bdmList
     * @param jdbcList
     * @param isIndex
     * @param generatorSql
     * @param jdbcList
     *        map. Key must be in lower case.
     */
    private void compareIndexOrConstraints(final BdmBusinessObject bdmBusinessObject, final List<BdmListOfFields> bdmList,
            final Map<String, TableListOfColums> jdbcList,
            final boolean isIndex, final GeneratorSql generatorSql,
            final OperationStatus operationStatus) {
        // / first, is the BdmList exist ?
        for (final BdmListOfFields bdmItem : bdmList) {
            // is this item exist ? do
            final TableListOfColums tableItem = jdbcList.get(bdmItem.getSqlName());
            if (tableItem == null) {
                // create it !
                if (isIndex) {
                    operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_INDEX_CREATE, "Index[" + bdmItem.name + "] (" + bdmItem.getListFields() + ")"), TypeMsg.ADD);
                    generatorSql.sqlCreateIndex(bdmItem);
                } else {
                    operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_CONSTRAINT_CREATE, "Constraint[" + bdmItem.name + "] (" + bdmItem.getListFields() + ")"), TypeMsg.ADD);
                    generatorSql.sqlCreateConstraint(bdmItem);
                }
            } else {
                if (!compareList(bdmItem, tableItem)) {
                    // change it !
                    if (isIndex) {
                        operationStatus.addDeltaEvent(bdmBusinessObject, null,
                                new BEvent(EVENT_INDEX_CHANGE, "Index change[" + bdmItem.name + "] : " + tableItem.toString() + "->" + bdmItem.toString()), TypeMsg.ALTER);
                        generatorSql.sqlDropIndex(bdmItem.getBusinessObject().getSqlTableName(), bdmItem.name);
                        generatorSql.sqlCreateIndex(bdmItem);
                    } else {
                        operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_CONSTRAINT_CHANGE, "Constraints change[" + bdmItem.name + "] : " + tableItem.toString() + "->" + bdmItem.toString()), TypeMsg.ALTER);
                        generatorSql.sqlDropConstraint(bdmBusinessObject.getSqlTableName(), bdmItem.name);
                        generatorSql.sqlCreateConstraint(bdmItem);
                    }
                }
            }
        }
        // may be too much list ?
        for (final TableListOfColums tableItem : jdbcList.values()) {
            boolean exist = false;

            // unique index on Persistenceid ? this one is not registered in the bdmList
            if (tableItem.isIndex && tableItem.getListColumns().size() == 1) {
                String item = tableItem.getListColumns().iterator().next();
                if ("PERSISTENCEID".equalsIgnoreCase(item))
                    exist = true;
            }

            for (final BdmListOfFields bdmIndex : bdmList) {
                if (tableItem.name.equals(bdmIndex.getSqlName())) {
                    exist = true;
                }
            }
            if (!exist) {
                if (isIndex) {
                    operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_INDEX_DISEAPEAR, "Index[" + tableItem.name + "] (" + tableItem.getListColumns() + ")"), TypeMsg.DROP);
                    generatorSql.sqlDropIndex(bdmBusinessObject.getSqlTableName(), tableItem.name);
                } else {

                    operationStatus.addDeltaEvent(bdmBusinessObject, null, new BEvent(EVENT_CONSTRAINT_DISEAPEAR, "Constraints[" + tableItem.name + "] (" + tableItem.getListColumns() + ")"), TypeMsg.DROP);
                    generatorSql.sqlDropConstraint(bdmBusinessObject.getSqlTableName(), tableItem.name);
                }
            }
        }
    }

    /**
     * compare index : the list of col must the the same.
     *
     * @param bdmIndex
     * @param tableIndex
     * @return
     */
    private boolean compareList(final BdmListOfFields bdmIndex, final TableListOfColums tableIndex) {
        if (bdmIndex.getListFields().size() != tableIndex.getListColumns().size()) {
            return false;
        }
        for (final String fieldIndexName : bdmIndex.getListFields()) {
            // compare with the bdmField.getSqlColName : the field maybe customerId but if this is a Relation field, the sqlField is customerId_pid
            final BdmField bdmField = bdmIndex.getBusinessObject().getFieldByFieldName(fieldIndexName);
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

    /* ******************************************************************************** */
    /*                                                                                  */
    /* Informations */
    /*                                                                                  */
    /*                                                                                  */
    /* ******************************************************************************** */

    /**
     * @return
     */
    public static HashMap<String, Object> getDatabaseInformation() {
        final HashMap<String, Object> result = new HashMap<String, Object>();
        Connection con = null;
        try {
            con = getConnection();

            if (con == null) {
                result.put("errormessage",
                        "Can't access the database using datasource [java:/comp/env/bonitaSequenceManagerDS] or [java:jboss/datasources/bonitaSequenceManagerDS]");
            } else {
                DatabaseMetaData databaseMetaData;
                databaseMetaData = con.getMetaData();

                result.put("DatabaseMajorVersion", databaseMetaData.getDatabaseMajorVersion());
                result.put("DatabaseMinorVersion", databaseMetaData.getDatabaseMinorVersion());
                result.put("DatabaseProductName", databaseMetaData.getDatabaseProductName());
                result.put("DatabaseProductVersion", databaseMetaData.getDatabaseProductVersion());
            }
        } catch (final SQLException e) {
            result.put("errormessage", e.toString());
        } finally {
            if (con != null)
                try {
                    con.close();
                } catch (Exception e) {
                }
        }

        return result;
    }

    private static Connection getConnection() {
        Context ctx = null;
        try {
            ctx = new InitialContext();
        } catch (final Exception e) {
            logger.info("Cant' get an InitialContext : can't access the datasource");
            return null;
        }

        DataSource ds = null;
        Connection con = null;
        try {
            ds = (DataSource) ctx.lookup("java:/comp/env/NotManagedBizDataDS");
            con = ds.getConnection();
            return con;
        } catch (final Exception e) {
        }
        try {
            if (ds == null) {
                ds = (DataSource) ctx.lookup("java:jboss/datasources/NotManagedBizDataDS");
                con = ds.getConnection();
                return con;
            }
        } catch (final Exception e) {
        } ;
        return null;
    }
}

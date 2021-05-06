package com.bonitasoft.custompage.snowmobile;

import java.util.ArrayList;
import java.util.List;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;


public class GeneratorConversionTables {
    
    private static BEvent EVENT_NOCONVERSION = new BEvent(GeneratorConversionTables.class.getName(), 1, Level.INFO,
            "No conversion table", "No conversion is found this Database type. The default SQL Translation is used", "The SQL Script will be generated using default SQL type", "Check Database, very proposed type");

    
    /**
     * conversion
     */
    public class ConversionItem {

        public String bdmType;
        
        /* some database (like Postgres) use a different wording to CREATE and to UPDATE
        * example : create table ...( purpose character varying(1024)
        * alter table travel alter column purpose TYPE varchar(1024)
        */
        public String jdbcSqlType;
        public String jdbcUpdateSqlType;
        public int jdbcType;
        public boolean lengthIsImportant = false;
        public Integer overrideLength = null;

        public ConversionItem(final String btmType, final String jdbcSqlType,final String jdbcUpdateSqlType, final int jdbcType, final boolean lengthIsImportant) {
            bdmType = btmType;
            this.jdbcSqlType = jdbcSqlType;
            this.jdbcUpdateSqlType = jdbcUpdateSqlType;
            this.jdbcType = jdbcType;
            this.lengthIsImportant = lengthIsImportant;
        }

        public ConversionItem(final String btmType, final String jdbcSqlType,final String jdbcUpdateSqlType, final int jdbcType, final boolean lengthIsImportant, int overrideLength) {
            bdmType = btmType;
            this.jdbcSqlType = jdbcSqlType;
            this.jdbcUpdateSqlType = jdbcUpdateSqlType;
            this.jdbcType = jdbcType;
            this.lengthIsImportant = lengthIsImportant;
            this.overrideLength = overrideLength;
        }
    }
    /**
     * 
     *  <businessObject qualifiedName="com.company.model.Parent">
            <fields>
                <field type="STRING" length="315" name="attributeString" nullable="true" collection="false"/>
                <field type="BOOLEAN" length="255" name="attributeBoolean" nullable="true" collection="false"/>
                <field type="LOCALDATE" length="255" name="attributeDateOnly" nullable="true" collection="false"/>
                <field type="LOCALDATETIME" length="255" name="attributeDateTimeNoTZ" nullable="true" collection="false"/>
                <field type="OFFSETDATETIME" length="255" name="attributeDateTimeTZ" nullable="true" collection="false"/>
                <field type="DOUBLE" length="255" name="attributeDouble" nullable="true" collection="false"/>
                <field type="FLOAT" length="255" name="attributeFloat" nullable="true" collection="false"/>
                <field type="INTEGER" length="255" name="attributeInteger" nullable="true" collection="false"/>
                <field type="LONG" length="255" name="attributeLong" nullable="true" collection="false"/>
                <field type="TEXT" length="255" name="attributeText" nullable="true" collection="false"/>
            </fields>
            <uniqueConstraints/>
            <queries/>
            <indexes/>
        </businessObject>
        
        
        
     * CREATE TABLE public.parent
(
    persistenceid bigint NOT NULL,
    attributeboolean boolean,
    attributedateonly character varying(10) COLLATE pg_catalog."default",
    attributedatetimenotz character varying(30) COLLATE pg_catalog."default",
    attributedatetimetz character varying(30) COLLATE pg_catalog."default",
    attributedouble double precision,
    attributefloat real,
    attributeinteger integer,
    attributelong bigint,
    attributestring character varying(315) COLLATE pg_catalog."default",
    attributetext text COLLATE pg_catalog."default",
    persistenceversion bigint,
    CONSTRAINT parent_pkey PRIMARY KEY (persistenceid)
)
     */
    private final ConversionItem[] postgresConversion = new ConversionItem[] {
            new ConversionItem("STRING", "character varying", "type varchar", java.sql.Types.VARCHAR, true),
            new ConversionItem("TEXT", "text", "text", java.sql.Types.CLOB, false),
            new ConversionItem("BOOLEAN", "boolean", "boolean", java.sql.Types.BIT, false),
            new ConversionItem("LONG", "bigint","bigint", java.sql.Types.BIGINT, false),
            new ConversionItem("INTEGER", "integer","integer",java.sql.Types.INTEGER, false),
            new ConversionItem("FLOAT", "real", "real",java.sql.Types.REAL, false),
            new ConversionItem("DOUBLE", "double precision", "double precision", java.sql.Types.DOUBLE, false),
            new ConversionItem("DOUBLE", "double precision", "double precision", java.sql.Types.NUMERIC, false),
            // Keep this one, because we can find a TIMESTAMP in the database
            new ConversionItem("DATE", "character varying", "type varchar", java.sql.Types.TIMESTAMP, true, 10),

            new ConversionItem("LOCALDATE", "character varying", "type varchar", java.sql.Types.VARCHAR, true, 10),
            new ConversionItem("LOCALDATETIME", "character varying", "type varchar", java.sql.Types.VARCHAR, true, 30),
            new ConversionItem("OFFSETDATETIME", "character varying", "type varchar", java.sql.Types.VARCHAR, true, 30),

            new ConversionItem("", "integer", "integer", java.sql.Types.INTEGER, false)

    };
    private final ConversionItem[] h2Conversion = new ConversionItem[] {
            new ConversionItem("STRING", "varchar", "varchar",java.sql.Types.VARCHAR, true),
            new ConversionItem("TEXT", "text", "text", java.sql.Types.CLOB, false),
            new ConversionItem("BOOLEAN", "boolean","boolean", java.sql.Types.BOOLEAN, false),
            new ConversionItem("LONG", "bigint","bigint", java.sql.Types.BIGINT, false),
            new ConversionItem("INTEGER", "integer","integer", java.sql.Types.INTEGER, false),
            new ConversionItem("FLOAT", "float", "float",java.sql.Types.DOUBLE, false),
            new ConversionItem("DOUBLE", "double precision", "double precision",  java.sql.Types.DOUBLE, false),
            new ConversionItem("DATE", "date", "date", java.sql.Types.TIMESTAMP, false),
            new ConversionItem("OFFSETDATETIME", "varchar","varchar", java.sql.Types.VARCHAR, true, 30),
            new ConversionItem("LOCALDATE", "varchar","varchar", java.sql.Types.VARCHAR, true, 10),
            new ConversionItem("LOCALDATETIME", "varchar","varchar", java.sql.Types.VARCHAR, true, 30),

            new ConversionItem("", "integer", "integer",  java.sql.Types.INTEGER, false)

    };
    private final ConversionItem[] sqlConversion = new ConversionItem[] {
            new ConversionItem("STRING", "varchar", "varchar",java.sql.Types.VARCHAR, true),
            new ConversionItem("TEXT", "text","text", java.sql.Types.VARCHAR, false),
            new ConversionItem("BOOLEAN", "boolean", "boolean",java.sql.Types.BIT, false),
            new ConversionItem("LONG", "bigint", "bigint",java.sql.Types.BIGINT, false),
            new ConversionItem("INTEGER", "integer",  "integer",java.sql.Types.INTEGER, false),
            new ConversionItem("FLOAT", "real", "real",java.sql.Types.REAL, false),
            new ConversionItem("DOUBLE", "double", "double",java.sql.Types.DOUBLE, false),
            new ConversionItem("DATE", "timestamp", "timestamp",java.sql.Types.TIMESTAMP, false),
            new ConversionItem("OFFSETDATETIME", "varchar", "varchar",java.sql.Types.VARCHAR, true, 30),
            new ConversionItem("LOCALDATE", "varchar","varchar", java.sql.Types.VARCHAR, true, 10),
            new ConversionItem("LOCALDATETIME", "varchar","varchar", java.sql.Types.VARCHAR, true, 30),
            new ConversionItem("", "integer","integer", java.sql.Types.INTEGER, false)

    };

    /**
     * @param databaseproductName
     * @return
     */
    public class ConversionTableResult {
        public ConversionItem[] conversionTable;
        public List<BEvent> listEvents = new ArrayList<>();
    }
    
    public ConversionTableResult getConversionTable(final String databaseproductName) {
        ConversionTableResult conversionTableResult = new ConversionTableResult();
        if ("PostgreSQL".equals(databaseproductName)) {
            conversionTableResult.conversionTable=postgresConversion;
        }
        else if ("H2".equals(databaseproductName)) {
            conversionTableResult.conversionTable=h2Conversion;
        }
        else {
            conversionTableResult.conversionTable=sqlConversion;
            conversionTableResult.listEvents.add( new BEvent( EVENT_NOCONVERSION, "Base[" + databaseproductName + "] "));
        }
        return conversionTableResult;
    }
}

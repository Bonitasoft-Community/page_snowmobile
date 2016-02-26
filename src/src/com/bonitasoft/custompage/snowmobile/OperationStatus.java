package com.bonitasoft.custompage.snowmobile;

import java.util.logging.Logger;

/**
 * this class contains all status for operation
 *
 * @author pierre-yves
 */
public class OperationStatus {

    private static Logger logger = Logger.getLogger(OperationStatus.class.getName());


    private String sql;
    private String presql;
    private String postsql;

    private String msg;
    private String deltamsg;

    private String errorMsg = null;

    private String headerMsg;
    private String sqlHeaderMsg;

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Error message */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void addErrorMsg(final String addMsg) {
        // don't add the same message
        if (errorMsg != null && errorMsg.indexOf(addMsg) != -1) {
            return;
        }
        logger.severe(addMsg);
        errorMsg = (errorMsg == null ? "" : errorMsg + ";") + addMsg;
    }

    public boolean isError() {
        return errorMsg != null;
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Conditionnal header */
    /*                                                       */
    /* ----------------------------------------------------- */
    /**
     * @param headerMsg
     * @return
     */
    public void addConditionalHeader(final String headerMsg)
    {
        this.headerMsg = headerMsg;
    }

    public void addSqlConditionalHeader(final String sqlHeaderMsg)
    {
        this.sqlHeaderMsg = sqlHeaderMsg;
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* message */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void addMsg(final String addMsg) {
        msg = (msg == null ? "" : msg + ";") + addMsg;
        System.out.println("MSG:" + addMsg);
    }

    public void addBusinessObject(final String businessName)
    {
        addMsg(getBigComment(businessName, false));
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Delta message */
    /*                                                       */
    /* ----------------------------------------------------- */
    /**
     * @param bdmBusinessObject
     * @param bdmField
     * @param addMsg
     */
    public void addDeltaMsg(final BdmBusinessObject bdmBusinessObject, final BdmField bdmField, final String addMsg) {
        String message = (bdmBusinessObject != null ? "BusinessObject[" + bdmBusinessObject.getName() + "] " : "")
                + (bdmField != null ? "Field[" + bdmField.name + "] " : "")
                + addMsg;

        if (headerMsg != null)
        {
            message = getBigComment(headerMsg, false) + message + "\n";
            headerMsg = null;
        }
        deltamsg = (deltamsg == null ? "" : deltamsg + ";") + message + "\n";
        addMsg(message);
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Sql message */
    /*                                                       */
    /* ----------------------------------------------------- */

    public void addSqlUpdate(final String addSql, final boolean isComment) {
        String commentSql = "";
        if (sqlHeaderMsg != null)
        {
            commentSql = getBigComment(sqlHeaderMsg, false);
            sqlHeaderMsg = null;
        }

        sql = (sql == null ? "" : sql + "\n")
                + commentSql
                + (isComment ? "-- " : "")
                + addSql;
        System.out.println("SQL:" + (isComment ? "-- " : "") + addSql);
    }

    /*
     * add a simple comment
     */
    public void addCommentUpdate(final String comment)
    {
        String commentSql = "";
        if (sqlHeaderMsg != null)
        {
            commentSql = getBigComment(sqlHeaderMsg, false);
            sqlHeaderMsg = null;
        }

        sql = (sql == null ? "" : sql + "\n")
                + commentSql
                + "-- " + comment;
        System.out.println("SQL:" + commentSql + "-- " + comment);
    }

    /**
     * all constraint who have to be remove like foreign key MUST be remove in first, then if a table B must be removed, all reference must be removed BEFORE
     * else the table drop will failed.
     * So all tables must be created AND THEN all reference must be added
     *
     * @param addSql
     */
    public void addSqlPreUpdate(final String addSql) {
        presql = (presql == null ? "" : presql + "\n") + addSql;
    }

    /**
     * all constraint like foreign key MUST be add at the end of the creation else the script will failed if the table A reference a table B and the table B
     * deos not exist.
     * So all tables must be created AND THEN all reference must be added
     *
     * @param addSql
     */
    public void addSqlPostUpdate(final String addSql) {
        postsql = (postsql == null ? "" : postsql + "\n") + addSql;
    }

    /* ----------------------------------------------------- */
    /*                                                       */
    /* Getter */
    /*                                                       */
    /* ----------------------------------------------------- */

    public String getSql() {
        if (presql == null && postsql == null && sql == null) {
            return null;
        }

        return (presql == null ? "" : presql) + "\n"
                + (sql == null ? "" : sql) + "\n"
                + (postsql == null ? "" : postsql);
    }

    public String getMsg() {
        return msg;
    }

    public String getDeltaMsg() {
        return deltamsg;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    private String getBigComment(final String comment, final boolean forSqlUse)
    {
        return "\n-- -----------------------------------------------------------------------------\n -- " + comment
                + "\n-- -----------------------------------------------------------------------------\n";
    }

}

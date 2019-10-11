import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.text.SimpleDateFormat;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.Runtime;

import org.json.simple.JSONObject;
import org.codehaus.groovy.tools.shell.CommandAlias;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;


import javax.naming.Context;
import javax.naming.InitialContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import java.sql.DatabaseMetaData;

import org.apache.commons.lang3.StringEscapeUtils

import org.bonitasoft.engine.identity.User;
import org.bonitasoft.console.common.server.page.PageContext
import org.bonitasoft.console.common.server.page.PageController
import org.bonitasoft.console.common.server.page.PageResourceProvider
import org.bonitasoft.engine.exception.AlreadyExistsException;
import org.bonitasoft.engine.exception.BonitaHomeNotSetException;
import org.bonitasoft.engine.exception.CreationException;
import org.bonitasoft.engine.exception.DeletionException;
import org.bonitasoft.engine.exception.ServerAPIException;
import org.bonitasoft.engine.exception.UnknownAPITypeException;

import org.bonitasoft.engine.api.TenantAPIAccessor;
import org.bonitasoft.engine.session.APISession;
import org.bonitasoft.engine.api.CommandAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.api.IdentityAPI;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.bpm.flownode.ActivityInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstanceSearchDescriptor;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedFlowNodeInstance;
import org.bonitasoft.engine.bpm.flownode.ArchivedActivityInstance;
import org.bonitasoft.engine.search.SearchOptions;
import org.bonitasoft.engine.search.SearchResult;

import org.bonitasoft.engine.command.CommandDescriptor;
import org.bonitasoft.engine.command.CommandCriterion;
import org.bonitasoft.engine.bpm.flownode.ActivityInstance;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.log.event.BEventFactory;

import com.bonitasoft.custompage.snowmobile.SnowMobileAccess;
import com.bonitasoft.custompage.snowmobile.OperationStatus;
import com.bonitasoft.custompage.snowmobile.SnowMobileAccess.ParametersCalcul;
 
public class Index implements PageController {

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response, PageResourceProvider pageResourceProvider, PageContext pageContext) {
	
		Logger logger= Logger.getLogger("org.bonitasoft");
		
		
		try {
			def String indexContent;
			pageResourceProvider.getResourceAsStream("Index.groovy").withStream { InputStream s-> indexContent = s.getText() };
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter()

			String action=request.getParameter("action");
			String bdmfile = request.getParameter("bdmfile");
			logger.info("#### SnowMobile:Groovy  action is["+action+"] bdmFile=["+bdmfile+"]");
			if (action==null || action.length()==0 )
			{
				runTheBonitaIndexDoGet( request, response,pageResourceProvider,pageContext);
				logger.info("#### SnowMobile:Groovy  - return resource");
				return;
			}
			
			logger.info("#### SnowMobile:Groovy  Check action now");
			HashMap<String,Object> resultUpdate = new HashMap<String,Object>();
				
			if ("info".equals(action))
			{
				SnowMobileAccess snowMobileAccess = new SnowMobileAccess();
				
				resultUpdate = snowMobileAccess.getDatabaseInformation();
				
			}
			else if ("calculupdate".equals(action))
			{
				logger.info("#### SnowMobile:Groovy sqlUpdate on bdmfile["+bdmfile+"]");
				OperationStatus operationStatus = new OperationStatus();
				SnowMobileAccess snowMobileAccess = new SnowMobileAccess();
				
				APISession apiSession = pageContext.getApiSession()
				ProcessAPI processAPI = TenantAPIAccessor.getProcessAPI(apiSession);
				snowMobileAccess.setContext( apiSession, pageResourceProvider.getPageDirectory(), processAPI);
				
				snowMobileAccess.setBdmFromFile(bdmfile, operationStatus);
				logger.info("#### SnowMobile:Groovy BdmFile OperationStatus["+BEventFactory.getHtml(operationStatus.getErrors())+"]");
				
				snowMobileAccess.setDatamodelFromDatabaseSource("NotManagedBizDataDS", operationStatus);
				logger.info("#### SnowMobile:Groovy Datasource OperationStatus["+BEventFactory.getHtml(operationStatus.getErrors())+"]");

				if (! operationStatus.isError())
				{
					ParametersCalcul parameter = new ParametersCalcul();
					parameter.commentDropTable = true;
					parameter.commentDropColumn = true;
					parameter.commentDropIndex = true;
					parameter.commentDropConstraint = true;
					parameter.commentExtraDropTables = true;
					
					if ("true".equals(request.getParameter("includeDropTable")))
					{
						parameter.commentDropTable = false;
						parameter.commentExtraDropTables = false;
					}
					if ("true".equals(request.getParameter("includeDropContent")))
					{
						parameter.commentDropColumn = false;
						parameter.commentDropIndex = false;
						parameter.commentDropConstraint = false;
					}
					snowMobileAccess.calculSqlScript(parameter, operationStatus);
				}	
				resultUpdate.put("sqlupdate", 		operationStatus.getSql());
				resultUpdate.put("errormessage", 	BEventFactory.getHtml( operationStatus.getErrors()));
				resultUpdate.put("deltamessage", 	operationStatus.getDeltaMsgList());
				resultUpdate.put("message", 	operationStatus.getMsg());
			}
			
			String jsonDetailsSt = JSONValue.toJSONString( resultUpdate );
			logger.info("#### SnowMobile:Groovy FinalResult ["+ jsonDetailsSt+"]");
				
			out.write( jsonDetailsSt );	
			out.flush();
			out.close();
			return;
			
			
			out.write( "Unknow command" );
			out.flush();
			out.close();
			return;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionDetails = sw.toString();
			logger.severe("#### SnowMobile:Groovy Exception ["+e.toString()+"] at "+exceptionDetails);
		}
	}

	
	/** -------------------------------------------------------------------------
	 *
	 *getIntegerParameter
	 * 
	 */
	private int getIntegerParameter(HttpServletRequest request, String paramName, int defaultValue)
	{
		String valueParamSt = request.getParameter(paramName);
		if (valueParamSt==null  || valueParamSt.length()==0)
		{
			return defaultValue;
		}
		int valueParam=defaultValue;
		try
		{
			valueParam = Integer.valueOf( valueParamSt );
		}
		catch( Exception e)
		{
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionDetails = sw.toString();
			
			logger.severe("#### SnowMobile:Groovy LongBoard: getinteger : Exception "+e.toString()+" on  ["+valueParamSt+"] at "+exceptionDetails );
			valueParam= defaultValue;
		}
		return valueParam;
	}
	
	/** -------------------------------------------------------------------------
	 *
	 *runTheBonitaIndexDoGet
	 * 
	 */
	private void runTheBonitaIndexDoGet(HttpServletRequest request, HttpServletResponse response, PageResourceProvider pageResourceProvider, PageContext pageContext) {
		try {
			def String indexContent;
			pageResourceProvider.getResourceAsStream("index.html").withStream { InputStream s->
					indexContent = s.getText()
			}
			 File pageDirectory = pageResourceProvider.getPageDirectory();
	      
			// def String pageResource="pageResource?&page="+ request.getParameter("page")+"&location=";
			// indexContent= indexContent.replace("@_USER_LOCALE_@", request.getParameter("locale"));
			// indexContent= indexContent.replace("@_PAGE_RESOURCE_@", pageResource);
		   indexContent= indexContent.replace("@_CURRENTTIMEMILIS_@", String.valueOf(System.currentTimeMillis()));
       indexContent= indexContent.replace("@_PAGEDIRECTORY_@", pageDirectory.getAbsolutePath()) ;

			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();
			out.print(indexContent);
			out.flush();
			out.close();
		} catch (Exception e) {
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      String exceptionDetails = sw.toString();
      
      logger.severe("#### SnowMobile:Groovy LongBoard: getinteger : Exception "+e.toString()+" at "+exceptionDetails );
    
		
		}
	}

}

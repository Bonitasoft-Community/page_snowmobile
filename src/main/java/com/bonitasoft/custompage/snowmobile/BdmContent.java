package com.bonitasoft.custompage.snowmobile;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.bonitasoft.custompage.snowmobile.BdmField.enumRelationType;

public class BdmContent {

    private static BEvent EVENT_BUSINESSOBJECT_NOTFOUND = new BEvent(BdmContent.class.getName(), 1, Level.APPLICATIONERROR,
            "Business object not found", "A Business Object is referenced, but not found.", "This relation is ignored", "Check the model, and fix it");
    private static BEvent EVENT_XMLPARSERERROR = new BEvent(BdmContent.class.getName(), 2, Level.APPLICATIONERROR,
            "XML Parser error", "The XML can't be read.", "The BDM can't be analysis", "Check the XML file");

    private static Logger logger = Logger.getLogger(BdmContent.class.getName());

    /**
     * the key is the name of the object (qualifiedname)
     */
    private HashMap<String, BdmBusinessObject> setBdmBusinessObject;

    /**
     * read from the BDM
     * 
     * @param xmlContent
     * @return
     */

    public void readFromXml(final String xmlContent, final OperationStatus operationStatus) {
        setBdmBusinessObject = new HashMap<String, BdmBusinessObject>();
        try {
            // logger.info("BosFile[" + bosName + "] bosContent[" + bosContent +
            // "]");

            final StringReader reader = new StringReader(xmlContent);
            final InputSource inputSource = new InputSource(reader);
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            final Document xmlDocument = builder.parse(inputSource);

            final Node rootNode = xmlDocument.getDocumentElement();
            Node businessObjectsNode = XmlToolbox.getNextChildElement(rootNode.getFirstChild());
            while (businessObjectsNode != null) {
                if (businessObjectsNode.getNodeName().equals("businessObjects")) {
                    Node oneBusinessObjectNode = XmlToolbox.getNextChildElement(businessObjectsNode.getFirstChild());
                    while (oneBusinessObjectNode != null) {
                        if (oneBusinessObjectNode.getNodeName().equals("businessObject")) {
                            final BdmBusinessObject bdmBusinessObject = new BdmBusinessObject();
                            bdmBusinessObject.readFromXml(oneBusinessObjectNode, operationStatus);
                            setBdmBusinessObject.put(bdmBusinessObject.getName(), bdmBusinessObject);

                        }
                        oneBusinessObjectNode = XmlToolbox.getNextChildElement(oneBusinessObjectNode.getNextSibling());
                    }

                }
                businessObjectsNode = XmlToolbox.getNextChildElement(businessObjectsNode.getNextSibling());
            }

            // now, we have to mark all relations COMPOSITION : the child must be noticed that a father point on him
            for (final BdmBusinessObject bdmBusinessObject : setBdmBusinessObject.values()) {
                for (final BdmField bdmField : bdmBusinessObject.listFields) {
                    if (bdmField.relationType == enumRelationType.COMPOSITION) {
                        final BdmBusinessObject businessReferenced = setBdmBusinessObject.get(bdmField.reference);
                        if (businessReferenced == null) {
                            operationStatus.addErrorEvent(new BEvent(EVENT_BUSINESSOBJECT_NOTFOUND, "BusinessObject[" + bdmField.reference + "] referenced in[" + bdmBusinessObject.getName()
                                    + "]"));
                        } else {
                            businessReferenced.addReferencedIn(bdmBusinessObject);
                        }
                    }
                }
            }

        } catch (final ParserConfigurationException e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            logger.severe("Error during exploring path " + exceptionDetails);
            operationStatus.addErrorEvent(new BEvent(EVENT_XMLPARSERERROR, exceptionDetails));
        } catch (final SAXException e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            operationStatus.addErrorEvent(new BEvent(EVENT_XMLPARSERERROR, exceptionDetails));
            return;
        } catch (final IOException e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            final String exceptionDetails = sw.toString();
            operationStatus.addErrorEvent(new BEvent(EVENT_XMLPARSERERROR, exceptionDetails));
            return;
        }
        return;

    }

    /**
     * return the Dbm in a Json format
     * 
     * @return
     */
    public ArrayList<HashMap<String, Object>> getJsonDescription() {
        final ArrayList<HashMap<String, Object>> description = new ArrayList<HashMap<String, Object>>();
        for (final BdmBusinessObject bdmBusinessObject : setBdmBusinessObject.values()) {

            description.add(bdmBusinessObject.getJsonDescription());
        }
        return description;
    }

    /**
     * @return
     */
    public HashMap<String, BdmBusinessObject> getListBdmBusinessObject() {
        return setBdmBusinessObject;
    }
}

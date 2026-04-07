package by.hgrosh.mockserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handles ERIP XML protocol requests from Hutki Grosh system.
 * Accepts form-data POST with "XML" parameter containing XML payload.
 */
@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);

    @PostMapping(value = {"", "/", "/erip", "/api", "/api/erip"}, 
            consumes = {"application/x-www-form-urlencoded", "multipart/form-data", "*/*"})
    public void handleEripRequest(HttpServletRequest request, HttpServletResponse response) {
        // Header Sniffing
        System.out.println("--- REQUEST HEADERS ---");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            System.out.println(name + ": " + request.getHeader(name));
        }

        String xmlIn = request.getParameter("XML");
        System.out.println(">>> INCOMING ERIP XML: " + xmlIn);
        
        if (xmlIn == null || xmlIn.isEmpty()) {
            System.out.println("!!! WARNING: Incoming XML is empty");
            return;
        }

        Map<String, String> data = parseEripXml(xmlIn);
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String requestId = data.getOrDefault("RequestId", "0");
        String account = data.getOrDefault("PersonalAccount", "12345678");
        String serviceNo = data.getOrDefault("ServiceNo", "13381001");

        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String outXml;
        
        if ("TransactionStart".equals(type)) {
            String myTrxId = String.valueOf(System.currentTimeMillis() / 1000); 
            outXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<TransactionStart>" +
                    "<ServiceProvider_TrxId>" + myTrxId + "</ServiceProvider_TrxId>" +
                    "<Info><InfoLine>TX: " + myTrxId + "</InfoLine></Info>" +
                    "</TransactionStart>" +
                    "</ServiceProvider_Response>";
        } else if ("TransactionResult".equals(type)) {
            outXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<TransactionResult />" +
                    "</ServiceProvider_Response>";
        } else {
            outXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<DateTime>" + now + "</DateTime>" +
                    "<ServiceNo>" + serviceNo + "</ServiceNo>" +
                    "<PersonalAccount>" + account + "</PersonalAccount>" +
                    "<Currency>933</Currency>" +
                    "<ResponseType>ServiceInfo</ResponseType>" +
                    "<Amount>40,00</Amount>" +
                    "<CanEditAmount>1</CanEditAmount>" +
                    "<CanEditName>0</CanEditName>" +
                    "<CanEditAddress>0</CanEditAddress>" +
                    "<ServiceInfo>" +
                    "<Amount Editable=\"N\" MinAmount=\"0,01\" MaxAmount=\"999999,99\">" +
                    "<Debt>40,00</Debt>" +
                    "</Amount>" +
                    "<Name><Surname>Медведев</Surname><FirstName>Дмитрий</FirstName></Name>" +
                    "<Address><City>Минск</City></Address>" +
                    "</ServiceInfo>" +
                    "</ServiceProvider_Response>";
        }

        System.out.println("<<< OUTGOING ERIP XML: " + outXml);

        response.reset();
        response.setStatus(HttpServletResponse.SC_OK);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("text/xml;charset=UTF-8");
        
        try {
            byte[] outBytes = outXml.getBytes(StandardCharsets.UTF_8);
            response.setContentLength(outBytes.length);
            try (OutputStream os = response.getOutputStream()) {
                os.write(outBytes);
                os.flush();
            }
        } catch (Exception e) {
            System.err.println("!!! ERROR SENDING RESPONSE: " + e.getMessage());
        }
    }

    private Map<String, String> parseEripXml(String xml) {
        Map<String, String> map = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            String[] tags = {"RequestType", "PersonalAccount", "RequestId", "ServiceNo"};
            for (String tag : tags) {
                NodeList nodes = doc.getElementsByTagName(tag);
                if (nodes.getLength() > 0) {
                    map.put(tag, nodes.item(0).getTextContent().trim());
                }
            }
        } catch (Exception e) {
            log.error("XML parse error: {}", e.getMessage());
        }
        return map;
    }
}

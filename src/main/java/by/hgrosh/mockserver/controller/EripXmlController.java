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
    public void handleEripRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String xmlIn = request.getParameter("XML");

        if (xmlIn == null || xmlIn.isEmpty()) {
            log.warn("ERIP: Incoming request has empty XML parameter");
            response.sendError(400, "Missing XML parameter");
            return;
        }

        log.info("ERIP XML received:\n{}", xmlIn);

        Map<String, String> data = parseEripXml(xmlIn);
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String requestId = data.getOrDefault("RequestId", "0");
        String account = data.getOrDefault("PersonalAccount", "unknown");
        String serviceNo = data.getOrDefault("ServiceNo", "13381001");

        log.info("ERIP: type={}, account={}, requestId={}, serviceNo={}", type, account, requestId, serviceNo);

        String now = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String outXml;
        
        if ("TransactionStart".equals(type)) {
            String myTrxId = "MOCK-TX-" + System.currentTimeMillis();
            outXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ServiceProvider_Response><Version>1</Version><RequestId>" + requestId + "</RequestId><Status>0</Status><ServiceNo>" + serviceNo + "</ServiceNo><ResponseType>TransactionStart</ResponseType><TransactionStart><ServiceProvider_TrxId>" + myTrxId + "</ServiceProvider_TrxId></TransactionStart></ServiceProvider_Response>";
        } else if ("TransactionResult".equals(type)) {
            outXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ServiceProvider_Response><Version>1</Version><RequestId>" + requestId + "</RequestId><Status>0</Status><ServiceNo>" + serviceNo + "</ServiceNo><ResponseType>TransactionResult</ResponseType></ServiceProvider_Response>";
        } else {
            // ServiceInfo - Aggressive tag doubling for compatibility
            outXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<DateTime>" + now + "</DateTime>" +
                    "<ServiceNo>" + serviceNo + "</ServiceNo>" +
                    "<ResponseType>ServiceInfo</ResponseType>" +
                    "<PersonalAccount>" + account + "</PersonalAccount>" +
                    "<LastName>Медведев</LastName>" +
                    "<lastName>Медведев</lastName>" +
                    "<Surname>Медведев</Surname>" +
                    "<FirstName>Дмитрий</FirstName>" +
                    "<firstName>Дмитрий</firstName>" +
                    "<PatronymicName></PatronymicName>" +
                    "<patronymicName></patronymicName>" +
                    "<City>Минск</City>" +
                    "<city>Минск</city>" +
                    "<Currency>933</Currency>" +
                    "<currency>BYN</currency>" +
                    "<Amount>40.00</Amount>" +
                    "<amount>40.00</amount>" +
                    "<Debt>40.00</Debt>" +
                    "<debt>40.00</debt>" +
                    "<Sum>40.00</Sum>" +
                    "<sum>40.00</sum>" +
                    "<CanEditAmount>0</CanEditAmount>" +
                    "<editableAmount>N</editableAmount>" +
                    "<ServiceInfo><Ticket>Счёт найден: " + account + ". Задолженность: 40.00 BYN</Ticket></ServiceInfo>" +
                    "</ServiceProvider_Response>";
        }

        log.info("ERIP Final Response (matches demo 2):\n{}", outXml);

        byte[] outBytes = outXml.getBytes(StandardCharsets.UTF_8);
        response.reset();
        response.setStatus(HttpServletResponse.SC_OK);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("text/xml;charset=UTF-8");
        response.setContentLength(outBytes.length);
        try (OutputStream os = response.getOutputStream()) {
            os.write(outBytes);
            os.flush();
        }
    }

    private Map<String, String> parseEripXml(String xml) throws Exception {
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

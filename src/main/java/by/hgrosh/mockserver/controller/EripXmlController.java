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
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);
    public static final List<String> xmlLogs = new ArrayList<>();
    private static final String ENCODING = "WINDOWS-1251";

    @GetMapping("/logs-xml")
    public List<String> getXmlLogs() {
        return xmlLogs;
    }

    @PostMapping(value = {"", "/", "/erip", "/api", "/api/erip"}, 
            consumes = {"application/x-www-form-urlencoded", "multipart/form-data", "*/*"})
    public void handleEripRequest(HttpServletRequest request, HttpServletResponse response) {
        String xmlIn = request.getParameter("XML");
        
        if (xmlIn == null || xmlIn.isEmpty()) {
            try {
                xmlIn = request.getReader().lines().collect(java.util.stream.Collectors.joining(System.lineSeparator()));
            } catch (Exception e) {
                log.error("Error reading body: " + e.getMessage());
            }
        }

        if (xmlIn != null && !xmlIn.isEmpty()) {
            xmlLogs.add(0, new Date().toString() + "\n" + xmlIn);
            if (xmlLogs.size() > 50) xmlLogs.remove(xmlLogs.size() - 1);
        }

        Map<String, String> data = parseEripXml(xmlIn != null ? xmlIn : "");
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String requestId = data.getOrDefault("RequestId", "0");
        String account = data.getOrDefault("PersonalAccount", "12345678");
        String serviceNo = data.getOrDefault("ServiceNo", "13381001");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"));
        String now = sdf.format(new Date());

        String outXml;

        // ЛОГИКА ОТВЕТА
        if ("Pay".equals(type)) {
            // ШАГ 2: Подтверждение оплаты
            String paymentNo = "PAY-" + (System.currentTimeMillis() / 1000);
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<DateTime>" + now + "</DateTime>" +
                    "<ServiceNo>" + serviceNo + "</ServiceNo>" +
                    "<RequestType>Pay</RequestType>" +
                    "<PersonalAccount>" + account + "</PersonalAccount>" +
                    "<PaymentNo>" + paymentNo + "</PaymentNo>" +
                    "<Amount>40.00</Amount>" +
                    "<Ticket><Line>Оплата принята</Line><Line>Чек: " + paymentNo + "</Line></Ticket>" +
                    "</ServiceProvider_Response>";
        } else if ("TransactionStart".equals(type) || "TransactionResult".equals(type)) {
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<ServiceNo>" + serviceNo + "</ServiceNo>" +
                    "<RequestType>" + type + "</RequestType>" +
                    "<TransactionStart><ServiceProvider_TrxId>TX-" + requestId + "</ServiceProvider_TrxId></TransactionStart>" +
                    "</ServiceProvider_Response>";
        } else {
            // ШАГ 1: Поиск счета (ServiceInfo)
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Version>1</Version>" +
                    "<RequestId>" + requestId + "</RequestId>" +
                    "<Status>0</Status>" +
                    "<DateTime>" + now + "</DateTime>" +
                    "<ServiceNo>" + serviceNo + "</ServiceNo>" +
                    "<RequestType>ServiceInfo</RequestType>" +
                    "<PersonalAccount>" + account + "</PersonalAccount>" +
                    "<Currency>933</Currency>" +
                    "<Amount>40.00</Amount>" + // Точка вместо запятой!
                    "<Fio>Медведев Дмитрий Эдуардович</Fio>" + // ФИО строкой
                    "<ServiceInfo>" +
                    "<Amount Editable=\"N\"><Debt>40.00</Debt></Amount>" +
                    "<Name>" +
                    "<Surname>Медведев</Surname><FirstName>Дмитрий</FirstName><Patronymic>Эдуардович</Patronymic>" +
                    "</Name>" +
                    "<Address><City>Минск</City><Street>Скрыганова</Street><House>6</House></Address>" +
                    "<Info><InfoLine>Счёт найден</InfoLine></Info>" +
                    "</ServiceInfo>" +
                    "</ServiceProvider_Response>";
        }

        try {
            byte[] outBytes = outXml.getBytes("Cp1251");
            response.reset();
            response.setStatus(HttpServletResponse.SC_OK);
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("text/xml;charset=windows-1251");
            response.setContentLength(outBytes.length);
            try (OutputStream os = response.getOutputStream()) {
                os.write(outBytes);
                os.flush();
            }
        } catch (Exception e) {
            log.error("Error sending response: " + e.getMessage());
        }
    }

    private Map<String, String> parseEripXml(String xml) {
        Map<String, String> map = new HashMap<>();
        if (xml == null || xml.isEmpty()) return map;
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
            log.error("XML parse error: " + e.getMessage());
        }
        return map;
    }
}
// Trigger re-deploy: commit for build retry after timeout
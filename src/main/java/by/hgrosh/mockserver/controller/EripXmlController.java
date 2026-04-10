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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles ERIP XML protocol requests from Hutki Grosh system.
 * Supports ServiceInfo (search) and Pay (payment) types.
 */
@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);
    private static final String ENCODING = "WINDOWS-1251";

    @PostMapping(value = { "", "/", "/erip", "/api/erip" }, consumes = { "application/x-www-form-urlencoded",
            "multipart/form-data", "*/*" })
    public void handleEripRequest(HttpServletRequest request, HttpServletResponse response) {
        
        // 1. Собираем заголовки для логов
        StringBuilder headLog = new StringBuilder("--- XML HEADERS ---\n");
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headLog.append(name).append(": ").append(request.getHeader(name)).append("\n");
        }

        // 2. Получаем XML
        String xmlIn = request.getParameter("XML");
        if (xmlIn == null || xmlIn.isEmpty()) {
            try {
                xmlIn = request.getReader().lines()
                        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
            } catch (Exception e) {}
        }

        if (xmlIn != null && !xmlIn.isEmpty()) {
            DataStore.logXml(new java.util.Date().toString() + "\n" + headLog.toString() + "\n" + xmlIn);
            log.info(">>> INCOMING XML: {}", xmlIn);
        }

        // 3. Парсим и готовим ответ
        Map<String, String> data = parseEripXml(xmlIn != null ? xmlIn : "");
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String account = data.getOrDefault("PersonalAccount", "12345678");

        String outXml;
        if ("TransactionStart".equals(type)) {
            String myTrxId = String.valueOf(System.currentTimeMillis() / 1000);
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<TransactionStart>" +
                    "<ServiceProvider_TrxId>" + myTrxId + "</ServiceProvider_TrxId>" +
                    "<Info><InfoLine>Operation: " + myTrxId + "</InfoLine></Info>" +
                    "</TransactionStart>" +
                    "</ServiceProvider_Response>";
        } else if ("TransactionResult".equals(type)) {
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<TransactionResult>" +
                    "<Info><InfoLine>Payment successful</InfoLine></Info>" +
                    "</TransactionResult>" +
                    "</ServiceProvider_Response>";
        } else {
            // ServiceInfo (Поиск)
            by.hgrosh.mockserver.model.DataStore.Invoice inv = by.hgrosh.mockserver.model.DataStore.invoiceStore.get(account);
            String echoSurname = (inv != null) ? inv.surname : "Medvedev";
            String echoFirstName = (inv != null) ? inv.firstName : "Dmitry";
            String echoAmount = (inv != null) ? inv.getAmountWithComma() : "40,00";

            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<ServiceInfo>" +
                    "<Amount Editable=\"Y\" MinAmount=\"0,01\" MaxAmount=\"100000\">" +
                    "<Debt>" + echoAmount + "</Debt><Penalty>0,00</Penalty><PayAmount>" + echoAmount + "</PayAmount>" +
                    "</Amount>" +
                    "<Name><Surname>" + echoSurname + "</Surname><FirstName>" + echoFirstName + "</FirstName><Patronymic>Eduardovich</Patronymic></Name>" +
                    "<Address><City>Minsk</City><Street>Skryganova</Street><House>6</House></Address>" +
                    "<Info><InfoLine>Account found</InfoLine></Info>" +
                    "</ServiceInfo>" +
                    "</ServiceProvider_Response>";
        }

        // 4. Отправка
        try {
            byte[] outBytes = outXml.getBytes(ENCODING);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/xml;charset=windows-1251");
            response.setContentLength(outBytes.length);
            response.getOutputStream().write(outBytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("XML send error: {}", e.getMessage());
        }
    }

    private Map<String, String> parseEripXml(String xml) {
        Map<String, String> map = new HashMap<>();
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            String[] tags = { "RequestType", "PersonalAccount" };
            for (String tag : tags) {
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName(tag);
                if (nodes.getLength() > 0) map.put(tag, nodes.item(0).getTextContent().trim());
            }
        } catch (Exception e) {}
        return map;
    }
}

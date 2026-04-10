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
    public static final List<String> xmlLogs = new ArrayList<>();
    private static final String ENCODING = "WINDOWS-1251";

    // Хранилище счетов в памяти
    private static final Map<String, Invoice> invoiceStore = new ConcurrentHashMap<>();

    static class Invoice {
        String account;
        String amount;
        String surname;
        String firstName;

        Invoice(String account, String amount, String surname, String firstName) {
            this.account = account;
            this.amount = amount;
            this.surname = surname;
            this.firstName = firstName;
        }
    }

    @GetMapping("/logs-xml")
    public List<String> getXmlLogs() {
        return xmlLogs;
    }

    @GetMapping("/register-invoice")
    public String registerInvoice(
            @RequestParam String account,
            @RequestParam String amount,
            @RequestParam(defaultValue = "Medvedev") String surname,
            @RequestParam(defaultValue = "Dmitry") String firstName) {
        
        // Нормализуем сумму (запятая)
        String normAmount = amount.replace(".", ",");
        if (!normAmount.contains(",")) normAmount += ",00";
        
        invoiceStore.put(account, new Invoice(account, normAmount, surname, firstName));
        return "OK: Invoice " + account + " registered for " + normAmount + " BYN";
    }

    @PostMapping(value = { "", "/", "/erip", "/api", "/api/erip" }, consumes = { "application/x-www-form-urlencoded",
            "multipart/form-data", "*/*" })
    public void handleEripRequest(HttpServletRequest request, HttpServletResponse response) {

        // 1. Собираем заголовки для логов
        StringBuilder headLog = new StringBuilder("--- HEADERS ---\n");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headLog.append(name).append(": ").append(request.getHeader(name)).append("\n");
        }
        System.out.println(headLog.toString());

        // 2. Получаем XML из параметра или сырого тела
        String xmlIn = request.getParameter("XML");
        if (xmlIn == null || xmlIn.isEmpty()) {
            try {
                xmlIn = request.getReader().lines()
                        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
            } catch (Exception e) {
                log.error("Error reading raw body: " + e.getMessage());
            }
        }

        if (xmlIn != null && !xmlIn.isEmpty()) {
            System.out.println(">>> INCOMING ERIP XML: " + xmlIn);
            String logEntry = new Date().toString() + "\n" + headLog.toString() + "\n" + xmlIn;
            xmlLogs.add(0, logEntry);
            if (xmlLogs.size() > 50)
                xmlLogs.remove(xmlLogs.size() - 1);
        }

        // 3. Парсим входящий XML
        Map<String, String> data = parseEripXml(xmlIn != null ? xmlIn : "");
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String rawRequestId = data.getOrDefault("RequestId", "");
        String requestId = (rawRequestId.matches("\\d+") && !rawRequestId.isEmpty()) ? rawRequestId : String.valueOf(System.currentTimeMillis() % 1000000);
        String account = data.getOrDefault("PersonalAccount", "12345678");
        String serviceNo = data.getOrDefault("ServiceNo", "13381001");
        String sessionId = "123456789";
        String sessionXml = "<SessionId>" + sessionId + "</SessionId>";

        String payAmount = data.get("PayAmount");
        String payAmountXml = payAmount != null && !payAmount.isEmpty() ? "<PayAmount>" + payAmount + "</PayAmount>" : "";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"));
        String now = sdf.format(new Date());

        // 4. Формируем ответ на основе RequestType (Строго КУИС)
        String outXml;

        if ("Pay".equals(type)) {
            String paymentNo = String.valueOf(System.currentTimeMillis() / 1000);
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<Pay>" +
                    "<PersonalAccount>" + account + "</PersonalAccount>" +
                    "<PaymentNo>" + paymentNo + "</PaymentNo>" +
                    "<Amount>40,00</Amount>" +
                    "<Ticket>" +
                    "<Line>Payment successful</Line>" +
                    "<Line>Check No: " + paymentNo + "</Line>" +
                    "</Ticket>" +
                    "</Pay>" +
                    "</ServiceProvider_Response>";

        } else if ("TransactionStart".equals(type)) {
            String myTrxId = String.valueOf(System.currentTimeMillis() / 1000);
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<TransactionStart>" +
                    "<ServiceProvider_TrxId>" + myTrxId + "</ServiceProvider_TrxId>" +
                    "<Info><InfoLine>Operation No: " + myTrxId + "</InfoLine></Info>" +
                    "</TransactionStart>" +
                    "</ServiceProvider_Response>";

        } else if ("TransactionResult".equals(type)) {
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<TransactionResult>" +
                    "<Info><InfoLine>Payment completed successfully</InfoLine></Info>" +
                    "</TransactionResult>" +
                    "</ServiceProvider_Response>";

        } else {
            // DEFAULT: ServiceInfo (Поиск счета)
            Invoice inv = invoiceStore.get(account);
            String echoSurname = (inv != null) ? inv.surname : "Medvedev";
            String echoFirstName = (inv != null) ? inv.firstName : "Dmitry";
            String echoAmount = (inv != null) ? inv.amount : "40,00";

            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                    "<ServiceProvider_Response>" +
                    "<ServiceInfo>" +
                    "<Amount Editable=\"Y\" MinAmount=\"0,01\" MaxAmount=\"100000\">" +
                    "<Debt>" + echoAmount + "</Debt>" +
                    "<Penalty>0,00</Penalty>" +
                    "<PayAmount>" + echoAmount + "</PayAmount>" +
                    "</Amount>" +
                    "<Name>" +
                    "<Surname>" + echoSurname + "</Surname>" +
                    "<FirstName>" + echoFirstName + "</FirstName>" +
                    "<Patronymic>Eduardovich</Patronymic>" +
                    "</Name>" +
                    "<Address><City>Minsk</City><Street>Skryganova</Street><House>6</House></Address>" +
                    "<Info><InfoLine>Account found</InfoLine></Info>" +
                    "</ServiceInfo>" +
                    "</ServiceProvider_Response>";
        }

        System.out.println("<<< OUTGOING ERIP XML: " + outXml);

        // 5. Отправка ответа в правильной кодировке
        try {
            byte[] outBytes = outXml.getBytes(ENCODING);
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
            log.error("!!! ERROR SENDING RESPONSE: " + e.getMessage());
        }
    }

    private Map<String, String> parseEripXml(String xml) {
        Map<String, String> map = new HashMap<>();
        if (xml == null || xml.isEmpty())
            return map;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            String[] tags = { "RequestType", "PersonalAccount", "RequestId", "ServiceNo", "Agent", "SessionId", "PayAmount", "Amount", "TransactionId", "DateTime", "Currency", "AuthorizationType" };
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

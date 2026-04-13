package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);
    private static final String ENCODING = "WINDOWS-1251";

    @Autowired
    private HutkiGroshJsonController jsonController;

    @PostMapping(value = { "", "/", "/erip", "/api/erip" })
    public void handleEripRequest(
            @RequestParam(value = "XML", required = false) String xmlParam,
            @RequestBody(required = false) String xmlBody,
            HttpServletResponse response) throws IOException {
        
        String xmlIn = (xmlParam != null && !xmlParam.isEmpty()) ? xmlParam : xmlBody;
        if (xmlIn == null || xmlIn.isEmpty()) return;

        log.info(">>> [BRIDGE] INCOMING XML: {}", xmlIn);
        DataStore.logXml(xmlIn);

        Map<String, String> data = parseEripXml(xmlIn);
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String account = data.getOrDefault("PersonalAccount", "unknown");
        String serviceNo = data.getOrDefault("ServiceNo", String.valueOf(DataStore.SERVICE_ID));
        String requestId = data.getOrDefault("RequestId", "1");
        String transactionId = data.getOrDefault("TransactionId", "");
        
        double amount = 0.0;
        try {
            String rawAmount = data.get("Amount");
            if (rawAmount != null) {
                amount = Double.parseDouble(rawAmount.replace(",", "."));
            }
        } catch (Exception e) {
            log.warn(">>> [BRIDGE] Amount parse failed for: {}", data.get("Amount"));
        }

        System.out.println(">>> [BRIDGE] Converting " + type + " for account: " + account);

        String outXml = "";
        try {
            if ("ServiceInfo".equals(type)) {
                HutkiGroshJsonController.AccountInfoRequest jsonReq = new HutkiGroshJsonController.AccountInfoRequest();
                jsonReq.type = "accountInfo";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.sessionId = "SID-" + (System.currentTimeMillis() % 10000);

                HutkiGroshJsonController.AccountInfoResponse jsonRes = jsonController.accountInfo(jsonReq);
                outXml = buildServiceInfoResponse(jsonRes, requestId);

            } else if ("TransactionStart".equals(type)) {
                HutkiGroshJsonController.SubmitPaymentRequest jsonReq = new HutkiGroshJsonController.SubmitPaymentRequest();
                jsonReq.type = "submitPayment";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.amount = (amount > 0) ? amount : 10.0;

                HutkiGroshJsonController.SubmitPaymentResponse jsonRes = jsonController.submitPayment(jsonReq);
                outXml = buildTransactionStartResponse(jsonRes, requestId, transactionId);

            } else if ("TransactionResult".equals(type)) {
                HutkiGroshJsonController.ConfirmPaymentRequest jsonReq = new HutkiGroshJsonController.ConfirmPaymentRequest();
                jsonReq.type = "confirmPayment";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.confirmed = true;
                
                String trxIdStr = data.getOrDefault("ServiceProvider_TrxId", "0");
                jsonReq.unipayTrxId = Long.parseLong(trxIdStr);

                jsonController.confirmPayment(jsonReq);
                outXml = buildTransactionResultResponse(requestId, transactionId, trxIdStr);
            }
        } catch (Exception e) {
            log.error("Bridge Error: {}", e.getMessage());
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\"?><ServiceProvider_Response><Error>" + e.getMessage() + "</Error></ServiceProvider_Response>";
        }

        log.info(">>> [BRIDGE] RESPONSE XML:\n{}", outXml);

        byte[] outBytes = outXml.getBytes(ENCODING);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/xml;charset=windows-1251");
        response.setContentLength(outBytes.length);
        response.getOutputStream().write(outBytes);
        response.getOutputStream().flush();
    }

    private String formatAmount(double amount) {
        return String.format("%.2f", amount).replace(".", ",");
    }

    private String buildServiceInfoResponse(HutkiGroshJsonController.AccountInfoResponse res, String requestId) {
        String debtStr = formatAmount(res.amount);
        return "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<ServiceInfo>" +
                "<SessionId>" + res.sessionId + "</SessionId>" +
                "<Amount Editable=\"Y\" MinAmount=\"0,01\" MaxAmount=\"10000\" Currency=\"933\">" +
                "<Debt>" + debtStr + "</Debt><Penalty>0,00</Penalty><PayAmount>" + debtStr + "</PayAmount>" +
                "</Amount>" +
                "<Name><Surname>" + res.clientName.surName + "</Surname>" +
                "<FirstName>" + res.clientName.firstName + "</FirstName>" +
                "<MiddleName/>" +
                "</Name>" +
                "<Address><City>Minsk</City></Address>" +
                "</ServiceInfo></ServiceProvider_Response>";
    }

    private String buildTransactionStartResponse(HutkiGroshJsonController.SubmitPaymentResponse res, String requestId, String transactionId) {
        return "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<TransactionStart>" +
                "<ServiceProvider_TrxId>" + res.unipayTrxId + "</ServiceProvider_TrxId>" +
                "<TransactionId>" + transactionId + "</TransactionId>" +
                "<Status>0</Status>" +
                "<Info><InfoLine>Оплата принята</InfoLine></Info>" +
                "</TransactionStart></ServiceProvider_Response>";
    }

    private String buildTransactionResultResponse(String requestId, String transactionId, String trxIdStr) {
        return "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<TransactionResult>" +
                "<ServiceProvider_TrxId>" + trxIdStr + "</ServiceProvider_TrxId>" +
                "<TransactionId>" + transactionId + "</TransactionId>" +
                "<Status>0</Status>" +
                "</TransactionResult>" +
                "</ServiceProvider_Response>";
    }

    private Map<String, String> parseEripXml(String xml) {
        Map<String, String> map = new HashMap<>();
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            org.xml.sax.InputSource is = new org.xml.sax.InputSource(new java.io.StringReader(xml));
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(is);
            
            String[] tags = { "RequestType", "PersonalAccount", "ServiceNo", "RequestId", "Amount", "ServiceProvider_TrxId", "TransactionId" };
            for (String tag : tags) {
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName(tag);
                if (nodes.getLength() > 0) {
                    map.put(tag, nodes.item(0).getTextContent().trim());
                }
            }
        } catch (Exception e) {}
        return map;
    }
}

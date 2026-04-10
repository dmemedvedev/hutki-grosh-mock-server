package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);
    private static final String ENCODING = "WINDOWS-1251";
    private static final String JSON_BASE_URL = "http://localhost:10000";

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping(value = { "", "/", "/erip", "/api/erip" })
    public void handleEripRequest(
            @RequestParam(value = "XML", required = false) String xmlParam,
            @RequestBody(required = false) String xmlBody,
            HttpServletResponse response) throws IOException {
        
        // 1. Пытаемся взять XML из параметра или из тела запроса
        String xmlIn = (xmlParam != null && !xmlParam.isEmpty()) ? xmlParam : xmlBody;
        
        if (xmlIn == null || xmlIn.isEmpty()) {
            log.warn(">>> [BRIDGE] Received empty XML request");
            return;
        }

        log.info(">>> [BRIDGE] INCOMING XML: {}", xmlIn);
        DataStore.logXml(xmlIn);

        Map<String, String> data = parseEripXml(xmlIn);
        String type = data.getOrDefault("RequestType", "ServiceInfo");
        String account = data.getOrDefault("PersonalAccount", "unknown");
        String serviceNo = data.getOrDefault("ServiceNo", String.valueOf(DataStore.SERVICE_ID));
        String requestId = data.getOrDefault("RequestId", "1");

        System.out.println(">>> [BRIDGE] Converting " + type + " for account: " + account);

        String outXml = "";
        try {
            if ("ServiceInfo".equals(type)) {
                HutkiGroshJsonController.AccountInfoRequest jsonReq = new HutkiGroshJsonController.AccountInfoRequest();
                jsonReq.type = "accountInfo";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.sessionId = "SID-" + (System.currentTimeMillis() % 10000);

                HutkiGroshJsonController.AccountInfoResponse jsonRes = restTemplate.postForObject(
                        JSON_BASE_URL + "/info", jsonReq, HutkiGroshJsonController.AccountInfoResponse.class);

                outXml = buildServiceInfoResponse(jsonRes, requestId);

            } else if ("TransactionStart".equals(type)) {
                HutkiGroshJsonController.SubmitPaymentRequest jsonReq = new HutkiGroshJsonController.SubmitPaymentRequest();
                jsonReq.type = "submitPayment";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.amount = 10.0; 

                HutkiGroshJsonController.SubmitPaymentResponse jsonRes = restTemplate.postForObject(
                        JSON_BASE_URL + "/submit", jsonReq, HutkiGroshJsonController.SubmitPaymentResponse.class);

                outXml = buildTransactionStartResponse(jsonRes, requestId);

            } else if ("TransactionResult".equals(type)) {
                HutkiGroshJsonController.ConfirmPaymentRequest jsonReq = new HutkiGroshJsonController.ConfirmPaymentRequest();
                jsonReq.type = "confirmPayment";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.confirmed = true;

                HutkiGroshJsonController.ConfirmPaymentResponse jsonRes = restTemplate.postForObject(
                        JSON_BASE_URL + "/commit", jsonReq, HutkiGroshJsonController.ConfirmPaymentResponse.class);

                outXml = buildTransactionResultResponse(requestId);
            }
        } catch (Exception e) {
            log.error("Bridge Error: {}", e.getMessage());
            outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\"?><Error>Bridge Failure: " + e.getMessage() + "</Error>";
        }

        // Send Response
        byte[] outBytes = outXml.getBytes(ENCODING);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/xml;charset=windows-1251");
        response.setContentLength(outBytes.length);
        response.getOutputStream().write(outBytes);
        response.getOutputStream().flush();
    }

    private String buildServiceInfoResponse(HutkiGroshJsonController.AccountInfoResponse res, String requestId) {
        return "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<ServiceInfo>" +
                "<SessionId>" + res.sessionId + "</SessionId>" +
                "<Amount Editable=\"Y\" MinAmount=\"0,01\" MaxAmount=\"10000\">" +
                "<Debt>" + res.amount + "</Debt><Penalty>0.00</Penalty><PayAmount>" + res.amount + "</PayAmount>" +
                "</Amount>" +
                "<Name><Surname>" + res.clientName.surName + "</Surname>" +
                "<FirstName>" + res.clientName.firstName + "</FirstName></Name>" +
                "<Address><City>Minsk</City></Address>" +
                "</ServiceInfo></ServiceProvider_Response>";
    }

    private String buildTransactionStartResponse(HutkiGroshJsonController.SubmitPaymentResponse res, String requestId) {
        return "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<TransactionStart>" +
                "<ServiceProvider_TrxId>" + res.unipayTrxId + "</ServiceProvider_TrxId>" +
                "<Info><InfoLine>Payment Initiated</InfoLine></Info>" +
                "</TransactionStart></ServiceProvider_Response>";
    }

    private String buildTransactionResultResponse(String requestId) {
        return "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<TransactionResult><Status>0</Status></TransactionResult>" +
                "</ServiceProvider_Response>";
    }

    private Map<String, String> parseEripXml(String xml) {
        Map<String, String> map = new HashMap<>();
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            String[] tags = { "RequestType", "PersonalAccount", "ServiceNo", "RequestId" };
            for (String tag : tags) {
                org.w3c.dom.NodeList nodes = doc.getElementsByTagName(tag);
                if (nodes.getLength() > 0) map.put(tag, nodes.item(0).getTextContent().trim());
            }
        } catch (Exception e) {}
        return map;
    }
}

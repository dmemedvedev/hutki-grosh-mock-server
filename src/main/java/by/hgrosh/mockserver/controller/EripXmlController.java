package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);
    private static final String ENCODING = "WINDOWS-1251";

    @Autowired
    private HutkiGroshJsonController jsonController;

    // Use RequestMapping to support both GET and POST for "New Protocol" testing
    @RequestMapping(
        value = { "/erip", "/api/erip", "/accountInfo", "/submitPayment", "/confirmPayment" },
        method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD, RequestMethod.OPTIONS }
    )
    public void handleEripRequest(
            @RequestParam(value = "XML", required = false) String xmlParam,
            @RequestBody(required = false) String xmlBody,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        String uri = request.getRequestURI();
        String method = request.getMethod();
        
        if ("HEAD".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(method)) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        Map<String, String> data = parseEripXml((xmlParam != null && !xmlParam.isEmpty()) ? xmlParam : xmlBody);

        String type = data.get("RequestType");
        if (type == null) {
            if (uri.contains("accountInfo")) type = "ServiceInfo";
            else if (uri.contains("submitPayment")) type = "TransactionStart";
            else if (uri.contains("confirmPayment")) type = "TransactionResult";
            else type = "ServiceInfo";
        }

        String account = data.getOrDefault("PersonalAccount", data.getOrDefault("account", "unknown"));
        String serviceNo = data.getOrDefault("ServiceNo", data.getOrDefault("serviceNo", String.valueOf(DataStore.SERVICE_ID)));
        String requestId = data.getOrDefault("RequestId", data.getOrDefault("requestId", "1"));
        String transactionId = data.getOrDefault("TransactionId", data.getOrDefault("transactionId", ""));
        
        log.info(">>> [BRIDGE] Processing {} for account={}", type, account);

        String outXml = "";
        try {
            if ("ServiceInfo".equals(type)) {
                HutkiGroshJsonController.AccountInfoRequest jsonReq = new HutkiGroshJsonController.AccountInfoRequest();
                jsonReq.type = "accountInfo";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);
                jsonReq.sessionId = "SID-" + (System.currentTimeMillis() % 10000);

                // Handle parameters from ERIP (if any)
                Map<String, String> eripParams = parseEripParameters((xmlParam != null && !xmlParam.isEmpty()) ? xmlParam : xmlBody);
                if (!eripParams.isEmpty()) {
                    jsonReq.parameterList = new ArrayList<>();
                    for (Map.Entry<String, String> entry : eripParams.entrySet()) {
                        HutkiGroshJsonController.ParameterValue pv = new HutkiGroshJsonController.ParameterValue();
                        pv.id = entry.getKey();
                        pv.value = entry.getValue();
                        jsonReq.parameterList.add(pv);
                    }
                }

                HutkiGroshJsonController.AccountInfoResponse jsonRes = jsonController.accountInfo(jsonReq, null);
                outXml = buildServiceInfoResponse(jsonRes, requestId);

            } else if ("TransactionStart".equals(type)) {
                HutkiGroshJsonController.SubmitPaymentRequest jsonReq = new HutkiGroshJsonController.SubmitPaymentRequest();
                jsonReq.type = "submitPayment";
                jsonReq.account = account;
                jsonReq.serviceId = Long.parseLong(serviceNo);

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
        response.setContentType("text/xml;charset=windows-1251");
        response.getOutputStream().write(outBytes);
        response.getOutputStream().flush();
    }

    private String buildServiceInfoResponse(HutkiGroshJsonController.AccountInfoResponse res, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>");
        sb.append("<ServiceProvider_Response>");
        sb.append("<Version>1</Version>");
        sb.append("<RequestId>").append(requestId).append("</RequestId>");
        sb.append("<ServiceInfo>");
        
        if ("ServiceInfo".equals(res.nextRqType) && res.parameterList != null) {
            // US_3: Return parameterList for gathering more info
            sb.append("<ParameterList>");
            for (DataStore.Parameter p : res.parameterList) {
                sb.append("<Parameter Name=\"").append(p.label).append("\" Id=\"").append(p.id).append("\">");
                sb.append("<Type>").append(p.type).append("</Type>");
                sb.append("<Label>").append(p.label).append("</Label>");
                sb.append("<Required>").append(p.required ? "Y" : "N").append("</Required>");
                sb.append("</Parameter>");
            }
            sb.append("</ParameterList>");
        } else {
            // Standard account display
            String debtStr = String.format("%.2f", res.amount).replace(".", ",");
            sb.append("<SessionId>").append(res.sessionId).append("</SessionId>");
            sb.append("<Amount Editable=\"Y\" MinAmount=\"0,01\" MaxAmount=\"1000\" Currency=\"933\">");
            sb.append("<Debt>").append(debtStr).append("</Debt><Penalty>0,00</Penalty><PayAmount>").append(debtStr).append("</PayAmount>");
            sb.append("</Amount>");
            sb.append("<Name><Surname>").append(res.clientName.surName).append("</Surname>");
            sb.append("<FirstName>").append(res.clientName.firstName).append("</FirstName>");
            sb.append("<MiddleName/></Name>");
            sb.append("<Address><City>Minsk</City></Address>");
        }
        
        sb.append("</ServiceInfo></ServiceProvider_Response>");
        return sb.toString();
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
        if (xml == null || xml.isEmpty()) return map;
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

    private Map<String, String> parseEripParameters(String xml) {
        Map<String, String> params = new HashMap<>();
        if (xml == null || xml.isEmpty()) return params;
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            org.xml.sax.InputSource is = new org.xml.sax.InputSource(new java.io.StringReader(xml));
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(is);
            
            org.w3c.dom.NodeList nodes = doc.getElementsByTagName("Parameter");
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) nodes.item(i);
                String id = el.getAttribute("Id");
                String value = el.getTextContent();
                if (id != null && !id.isEmpty()) {
                    params.put(id, value);
                }
            }
        } catch (Exception e) {}
        return params;
    }
}

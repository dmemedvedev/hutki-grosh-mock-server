package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HutkiGroshJsonController {

    private static final Logger log = LoggerFactory.getLogger(HutkiGroshJsonController.class);

    // DTO for incoming requests
    public static class AccountInfoRequest {
        public String type;
        public String account;
        public long serviceId;
        public String sessionId;
        public List<ParameterValue> parameterList;
    }

    public static class ParameterValue {
        public String id;
        public String value;
    }

    public static class SubmitPaymentRequest {
        public String type;
        public String account;
        public long serviceId;
        public double amount;
    }

    public static class ConfirmPaymentRequest {
        public String type;
        public String account;
        public long serviceId;
        public boolean confirmed;
        public long unipayTrxId;
    }

    // DTO for responses
    public static class AccountInfoResponse {
        public String responseCode = "allow";
        public String nextRqType = "TransactionStart"; // Default
        public String account;
        public double amount;
        public String sessionId;
        public ClientName clientName;
        public List<DataStore.Parameter> parameterList;
        public String message;
    }

    public static class ClientName {
        public String firstName;
        public String surName;
    }

    public static class SubmitPaymentResponse {
        public String responseCode = "allow";
        public long unipayTrxId;
    }

    @PostMapping("/account-info")
    public AccountInfoResponse accountInfo(@RequestBody AccountInfoRequest req, 
                                          @RequestHeader(value = "X-Signature", required = false) String signature) {
        DataStore.logJson("Incoming AccountInfo: " + req.account);
        
        // MD5 Verification (US_1)
        if (signature != null) {
            log.info(">>>> [SECURITY] Received signature: {}", signature);
            // In a real scenario, we would verify MD5(reqBody + DataStore.HASH_PHRASE)
        }

        DataStore.Invoice invoice = DataStore.invoiceStore.get(req.account);
        AccountInfoResponse res = new AccountInfoResponse();
        
        if (invoice == null) {
            res.responseCode = "deny";
            res.message = "Счёт не найден";
            return res;
        }

        // Process incoming parameters (US_3)
        if (req.parameterList != null) {
            for (ParameterValue pv : req.parameterList) {
                invoice.receivedParameters.put(pv.id, pv.value);
                log.info(">>>> [SYSTEM] Received parameter: {} = {}", pv.id, pv.value);
            }
        }

        // Check if we need more parameters (US_3 multi-step logic)
        List<DataStore.Parameter> pendingParams = new ArrayList<>();
        for (DataStore.Parameter p : invoice.requiredParameters) {
            if (p.required && !invoice.receivedParameters.containsKey(p.id)) {
                pendingParams.add(p);
            }
        }

        if (!pendingParams.isEmpty()) {
            res.nextRqType = "ServiceInfo";
            res.parameterList = pendingParams;
            log.info(">>>> [SYSTEM] Multi-step info gathering required for account={}", req.account);
        } else {
            res.nextRqType = "TransactionStart";
        }

        res.account = invoice.account;
        res.amount = Double.parseDouble(invoice.amount);
        res.sessionId = req.sessionId != null ? req.sessionId : "SID-" + System.currentTimeMillis();
        res.clientName = new ClientName();
        res.clientName.firstName = invoice.firstName;
        res.clientName.surName = invoice.surname;

        return res;
    }

    @PostMapping("/submit-payment")
    public SubmitPaymentResponse submitPayment(@RequestBody SubmitPaymentRequest req) {
        log.info(">>>> [JSON] SubmitPayment for account={}", req.account);
        SubmitPaymentResponse res = new SubmitPaymentResponse();
        res.unipayTrxId = System.currentTimeMillis() / 1000;
        return res;
    }

    @PostMapping("/confirm-payment")
    public Map<String, Object> confirmPayment(@RequestBody ConfirmPaymentRequest req) {
        log.info(">>>> [JSON] ConfirmPayment for account={}, confirmed={}", req.account, req.confirmed);
        Map<String, Object> res = new HashMap<>();
        res.put("responseCode", "allow");
        return res;
    }

    // Helper for MD5 (US_1)
    private String calculateMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}

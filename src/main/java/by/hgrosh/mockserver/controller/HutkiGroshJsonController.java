package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.Locale;

@RestController
@RequestMapping(value = { "/api", "/" })
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
        public String status = "ok";
        public String responseCode = "allow";
        public String nextRqType = "TransactionStart";
        public String account;
        public String billId = "";
        public double amount;
        public double totalAmount;
        public double payAmount;
        public String debt = "";
        public String editable = "Y";
        public int raCode = 1;
        public double penalty = 0.0;
        public String sessionId;
        public ClientName clientName = new ClientName();
        public Address address = new Address();
        public List<DataStore.Parameter> parameterList = new ArrayList<>();
        public String message = "";
    }

    public static class ClientName {
        public String firstName;
        public String surName;
    }

    public static class Address {
        public String city = "Minsk";
        public String street = "";
        public String house = "";
    }

    public static class SubmitPaymentResponse {
        public String responseCode = "allow";
        public long unipayTrxId;
    }

    @RequestMapping(value = { "/accountInfo", "/account-info" }, method = { RequestMethod.GET, RequestMethod.POST })
    public AccountInfoResponse accountInfo(@RequestBody(required = false) AccountInfoRequest req, 
                                          @RequestHeader(value = "X-Signature", required = false) String signature,
                                          @RequestParam(required = false) String account) {
        
        // Handle both JSON body and Query Parameters (for GET tests)
        if (req == null) {
            req = new AccountInfoRequest();
            req.account = account;
            req.type = "accountInfo";
        } else if (req.account == null) {
            req.account = account;
        }

        DataStore.logJson("Incoming AccountInfo: " + req.account);
        
        // MD5 Verification (US_1)
        if (signature != null) {
            log.info(">>>> [SECURITY] Received signature: {}", signature);
        }

        DataStore.Invoice invoice = DataStore.invoiceStore.get(req.account != null ? req.account : "");
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
        double amountVal = Double.parseDouble(invoice.amount);
        res.amount = amountVal;
        res.totalAmount = amountVal;
        res.payAmount = amountVal;
        res.debt = String.format(Locale.US, "%.2f", amountVal).replace(".", ",");
        res.editable = "Y";
        res.sessionId = req.sessionId != null ? req.sessionId : String.valueOf(System.currentTimeMillis() % 10000000);
        
        res.clientName.firstName = invoice.firstName;
        res.clientName.surName = invoice.surname;
        res.address = new Address();
        res.address.city = "Minsk";

        if (invoice.account.equals("multistep")) {
            DataStore.Parameter p = new DataStore.Parameter("counter_reading", "Показания счетчика", "p", true);
            res.parameterList = Collections.singletonList(p);
            res.nextRqType = "ServiceInfo";
        } else {
            res.parameterList = new ArrayList<>();
            res.nextRqType = "TransactionStart";
        }

        return res;
    }

    @RequestMapping(value = { "/submitPayment", "/submit-payment" }, method = { RequestMethod.GET, RequestMethod.POST })
    public SubmitPaymentResponse submitPayment(@RequestBody(required = false) SubmitPaymentRequest req,
                                              @RequestParam(required = false) String account) {
        if (req == null) {
            req = new SubmitPaymentRequest();
            req.account = account;
        }
        log.info(">>>> [JSON] SubmitPayment for account={}", req.account);
        SubmitPaymentResponse res = new SubmitPaymentResponse();
        res.unipayTrxId = System.currentTimeMillis() / 1000;
        return res;
    }

    @RequestMapping(value = { "/confirmPayment", "/confirm-payment" }, method = { RequestMethod.GET, RequestMethod.POST })
    public Map<String, Object> confirmPayment(@RequestBody(required = false) ConfirmPaymentRequest req,
                                             @RequestParam(required = false) String account) {
        if (req == null) {
            req = new ConfirmPaymentRequest();
            req.account = account;
            req.confirmed = true;
        }
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

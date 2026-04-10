package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HutkiGroshJsonController {

    private static final Logger log = LoggerFactory.getLogger(HutkiGroshJsonController.class);

    // --- DTO CLASSES ---

    public static class ClientName {
        public String firstName;
        public String middleName;
        public String surName;
    }

    public static class ClientAddress {
        public String city;
        public String street;
        public String house;
        public String building;
        public String apartment;
    }

    public static class Parameter {
        public int idx;
        public String name;
        public String edit;
        public String value;
        public String dataType;
        public String dataFormat;
        public Integer minLength;
        public Integer maxLength;
        public String hint;
    }

    public static class AccountInfoRequest {
        public String type;
        public long serviceId;
        public String account;
        public String sessionId;
        public int raCode;
        public List<Parameter> parameterList;
    }

    public static class AccountInfoResponse {
        public String nextRqType = "TransactionStart";
        public List<String> ticket = new ArrayList<>();
        public String responseCode = "allow";
        public String sessionId;
        public double amount;
        public boolean editable = true;
        public double minAmount = 1.0;
        public double maxAmount = 999999.0;
        public String editFIO = "deny";
        public ClientName clientName;
        public String editAddress = "deny";
        public ClientAddress address;
        public List<Parameter> parameterList;
    }

    public static class SubmitPaymentRequest {
        public String type;
        public long serviceId;
        public double amount;
        public int curAmount;
        public double exRate;
        public double amountBYR;
        public long raCode;
        public long transactionId;
        public String account;
        public String authType;
        public String clientFIO;
        public ClientName clientName;
        public String clientAddress;
        public ClientAddress address;
        public String billId;
        public String sessionId;
        public List<Parameter> parameterList;
    }

    public static class SubmitPaymentResponse {
        public String responseCode = "allow";
        public long unipayTrxId;
        public List<String> ticket = new ArrayList<>();
    }

    public static class ConfirmPaymentRequest {
        public String type;
        public long serviceId;
        public long billNumber;
        public long esasTransactionId;
        public boolean confirmed;
        public long unipayTrxId;
        public String account;
        public double amount;
        public String errorText;
    }

    public static class ConfirmPaymentResponse {
        public String responseCode = "allow";
        public List<String> ticket = new ArrayList<>();
    }

    // --- ENDPOINTS ---

    @PostMapping("/info")
    public AccountInfoResponse accountInfo(@RequestBody AccountInfoRequest req) {
        log.info(">>> JSON accountInfo: account={}", req.account);
        DataStore.logJson("Request: accountInfo, account=" + req.account);

        AccountInfoResponse res = new AccountInfoResponse();
        res.sessionId = req.sessionId;

        DataStore.Invoice inv = DataStore.invoiceStore.get(req.account);
        if (inv != null) {
            res.amount = inv.getAmountAsDouble();
            res.clientName = new ClientName();
            res.clientName.firstName = inv.firstName;
            res.clientName.surName = inv.surname;
            res.clientName.middleName = "Eduardovich";
        } else {
            res.amount = 40.0;
            res.clientName = new ClientName();
            res.clientName.firstName = "Dmitry";
            res.clientName.surName = "Medvedev";
            res.clientName.middleName = "Eduardovich";
        }

        res.ticket.add("Account found: " + req.account);
        res.ticket.add("Current debt: " + res.amount + " BYN");

        return res;
    }

    @PostMapping("/submit")
    public SubmitPaymentResponse submitPayment(@RequestBody SubmitPaymentRequest req) {
        log.info(">>> JSON submitPayment: account={}, amount={}", req.account, req.amount);
        DataStore.logJson("Request: submitPayment, account=" + req.account + ", amount=" + req.amount);

        SubmitPaymentResponse res = new SubmitPaymentResponse();
        res.unipayTrxId = System.currentTimeMillis() / 1000;
        res.ticket.add("Payment successfully initiated.");
        res.ticket.add("Transaction ID: " + res.unipayTrxId);

        return res;
    }

    @PostMapping("/commit")
    public ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest req) {
        log.info(">>> JSON confirmPayment: trxId={}, confirmed={}", req.unipayTrxId, req.confirmed);
        DataStore.logJson("Request: confirmPayment, confirmed=" + req.confirmed);

        ConfirmPaymentResponse res = new ConfirmPaymentResponse();
        if (req.confirmed) {
            res.ticket.add("Payment successfully completed.");
            res.ticket.add("Thank you!");
        } else {
            res.responseCode = "deny";
            res.ticket.add("Payment cancelled or failed.");
            res.ticket.add("Reason: " + req.errorText);
        }

        return res;
    }
}

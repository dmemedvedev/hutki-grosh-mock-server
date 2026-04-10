package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/json")
@CrossOrigin(origins = "*")
public class HutkiGroshJsonController {

    private static final Logger log = LoggerFactory.getLogger(HutkiGroshJsonController.class);
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private void logJson(String title, Object obj) {
        try {
            String json = mapper.writeValueAsString(obj);
            log.info(">>> [JSON-DEBUG] {}:\n{}", title, json);
        } catch (Exception e) {
            log.error("Failed to log JSON: {}", e.getMessage());
        }
    }

    @PostMapping("/info")
    public AccountInfoResponse accountInfo(@RequestBody AccountInfoRequest req) {
        logJson("INCOMING AccountInfoRequest", req);
        
        AccountInfoResponse res = new AccountInfoResponse();
        DataStore.Invoice inv = DataStore.invoiceStore.get(req.account);
        
        if (inv != null) {
            res.responseCode = "allow";
            res.nextRqType = "TransactionStart";
            res.sessionId = req.sessionId; // Echo back the session ID
            res.amount = inv.getAmountAsDouble();
            res.editable = true;
            res.clientName = new ClientName();
            res.clientName.firstName = inv.firstName;
            res.clientName.surName = inv.surname;
            res.ticket.add("Счет найден в системе ПУ");
            res.ticket.add("Сумма к оплате: " + inv.amount + " BYN");
        } else {
            res.responseCode = "deny";
            res.ticket.add("Ошибка: Лицевой счет не найден в базе ПУ");
        }
        
        logJson("OUTGOING AccountInfoResponse", res);
        return res;
    }

    @PostMapping("/submit")
    public SubmitPaymentResponse submitPayment(@RequestBody SubmitPaymentRequest req) {
        logJson("INCOMING SubmitPaymentRequest", req);
        
        SubmitPaymentResponse res = new SubmitPaymentResponse();
        res.responseCode = "allow";
        res.unipayTrxId = System.currentTimeMillis() / 1000;
        res.ticket.add("Оплата инициирована");
        
        logJson("OUTGOING SubmitPaymentResponse", res);
        return res;
    }

    @PostMapping("/commit")
    public ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest req) {
        logJson("INCOMING ConfirmPaymentRequest", req);
        
        ConfirmPaymentResponse res = new ConfirmPaymentResponse();
        res.responseCode = "allow";
        res.ticket.add("Оплата подтверждена");
        
        logJson("OUTGOING ConfirmPaymentResponse", res);
        return res;
    }

    // DTO Classes
    public static class AccountInfoRequest {
        public String type;
        public long serviceId;
        public String account;
        public String sessionId;
    }

    public static class AccountInfoResponse {
        public String responseCode;
        public String nextRqType;
        public String sessionId;
        public double amount;
        public boolean editable;
        public double minAmount = 0.01;
        public double maxAmount = 99999.0;
        public ClientName clientName;
        public List<String> ticket = new ArrayList<>();
    }

    public static class ClientName {
        public String firstName;
        public String surName;
        public String middleName = "";
    }

    public static class SubmitPaymentRequest {
        public String type;
        public long serviceId;
        public String account;
        public double amount;
    }

    public static class SubmitPaymentResponse {
        public String responseCode;
        public long unipayTrxId;
        public List<String> ticket = new ArrayList<>();
    }

    public static class ConfirmPaymentRequest {
        public String type;
        public long serviceId;
        public long unipayTrxId;
        public boolean confirmed;
        public String account;
        public String errorText;
    }

    public static class ConfirmPaymentResponse {
        public String responseCode;
        public List<String> ticket = new ArrayList<>();
    }
}

package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
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

    @PostMapping("/info")
    public AccountInfoResponse accountInfo(@RequestBody AccountInfoRequest req) {
        log.info(">>> [JSON] accountInfo request for: {}", req.account);
        
        AccountInfoResponse res = new AccountInfoResponse();
        DataStore.Invoice inv = DataStore.invoiceStore.get(req.account);
        
        if (inv != null) {
            res.responseCode = "allow";
            res.nextRqType = "TransactionStart";
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
        
        return res;
    }

    @PostMapping("/submit")
    public SubmitPaymentResponse submitPayment(@RequestBody SubmitPaymentRequest req) {
        log.info(">>> [JSON] submitPayment request for: {}, amount: {}", req.account, req.amount);
        
        SubmitPaymentResponse res = new SubmitPaymentResponse();
        res.responseCode = "allow"; // ОБЯЗАТЕЛЬНО для ТХГ
        res.unipayTrxId = System.currentTimeMillis() / 1000;
        res.ticket.add("Оплата инициирована");
        
        return res;
    }

    @PostMapping("/commit")
    public ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest req) {
        log.info(">>> [JSON] confirmPayment request for trx: {}, confirmed: {}", req.unipayTrxId, req.confirmed);
        
        ConfirmPaymentResponse res = new ConfirmPaymentResponse();
        res.responseCode = "allow"; // ОБЯЗАТЕЛЬНО для ТХГ
        res.ticket.add("Оплата подтверждена");
        
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

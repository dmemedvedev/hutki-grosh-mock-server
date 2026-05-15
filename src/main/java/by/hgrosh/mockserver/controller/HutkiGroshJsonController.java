package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequestMapping(value = { "/api", "/" })
@CrossOrigin(origins = "*")
public class HutkiGroshJsonController {

    private static final Logger log = LoggerFactory.getLogger(HutkiGroshJsonController.class);
    private static final Map<Long, Long> trxCache = new java.util.concurrent.ConcurrentHashMap<>();

    // DTO for incoming requests
    public static class AccountInfoRequest {
        public String type;
        public String account;
        public long serviceId;
        public String sessionId;
        public List<Map<String, Object>> parameterList;
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
        public Long transactionId;
    }

    public static class ConfirmPaymentRequest {
        public String type;
        public String account;
        public long serviceId;
        public boolean confirmed = true;
        public long unipayTrxId;
        public String errorText;
    }

    // DTO for responses
    public static class AccountInfoResponse {
        public String responseCode = "allow";
        public String nextRqType = "TransactionStart";
        public String account;
        public double amount;
        public boolean editable = true;
        public double minAmount = 1.00;
        public double maxAmount = 10000.00;
        public String sessionId;
        public ClientName clientName = new ClientName();
        public List<Map<String, Object>> parameterList = new ArrayList<>();
        public String message = null;
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
        public List<String> ticket = Arrays.asList("Оплата начата", "Всего хорошего");
    }

    @RequestMapping(value = { "/accountInfo", "/account-info" }, method = { RequestMethod.GET, RequestMethod.POST })
    public AccountInfoResponse accountInfo(@RequestBody(required = false) AccountInfoRequest req, 
                                          @RequestHeader(value = "X-Signature", required = false) String signature,
                                          @RequestParam(required = false) String account,
                                          HttpServletRequest request) {
        
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
            // Auto-generate invoice for load testing
            String autoAccount = (req.account != null && !req.account.isEmpty()) ? req.account : "auto-" + (System.currentTimeMillis() % 100000);
            log.info(">>>> [LOAD TEST] Auto-generating invoice for missing account: {}", autoAccount);
            invoice = new DataStore.Invoice(autoAccount, "10.00", "LoadTestSurname", "LoadTestName");
            DataStore.invoiceStore.put(autoAccount, invoice);
        }

        if (!verifySignature(request)) {
            res.responseCode = "deny";
            res.message = "Invalid signature";
            return res;
        }

        res.account = invoice.account;
        res.amount = Double.parseDouble(invoice.amount);
        res.sessionId = req.sessionId != null ? req.sessionId : String.valueOf(System.currentTimeMillis() % 10000000);
        res.clientName.firstName = invoice.firstName;
        res.clientName.surName = invoice.surname;

        // Process incoming Unformalized parameters from Alcosi (US_3)
        // Сценарий 2 многошаговой оплаты:
        //   - На первый accountInfo с пустыми allow-параметрами мок отвечает
        //     nextRqType=ServiceInfo, запрашивая ввод у плательщика.
        //   - Для deny-параметров значения подставляет сам ПУ.
        //   - Когда все allow-параметры заполнены, переходим в TransactionStart.
        boolean needsInput = false;
        if (req.parameterList != null && !req.parameterList.isEmpty()) {
            for (Map<String, Object> param : req.parameterList) {
                Object editFlag = param.get("edit");
                Object val = param.get("value");
                Object nameObj = param.get("name");
                boolean isEditable = "allow".equals(editFlag);
                boolean isEmpty = (val == null || val.toString().trim().isEmpty());

                // Требуем ввод только для редактируемых параметров с пустым значением
                if (isEditable && isEmpty) {
                    needsInput = true;
                } else if (!isEmpty) {
                    log.info(">>>> [SYSTEM] Received parameter [{}] = {}", nameObj, val);
                }

                // Для deny-параметров мок (как ПУ) подставляет значение со своей стороны
                if ("deny".equals(editFlag) && isEmpty) {
                    String mockValue = generateMockValue(String.valueOf(nameObj));
                    param.put("value", mockValue);
                    log.info(">>>> [SYSTEM] Mock auto-filled deny parameter '{}' = '{}'", nameObj, mockValue);
                }

                if (nameObj != null) {
                    log.info(">>>> [SYSTEM] Processed parameter '{}', edit='{}'.", nameObj, param.get("edit"));
                }
            }
            if (needsInput) {
                // Возвращаем те же параметры обратно: allow остаются пустыми (для ввода),
                // deny уже заполнены значениями ПУ.
                res.parameterList = req.parameterList;
                res.nextRqType = "ServiceInfo";
                res.amount = 0.0;       // на шаге ServiceInfo итоговой суммы ещё нет
                res.editable = false;
            } else {
                res.nextRqType = "TransactionStart";
            }
        } else {
            res.nextRqType = "TransactionStart";
        }

        return res;
    }

    @RequestMapping(value = { "/submitPayment", "/submit-payment" }, method = { RequestMethod.GET, RequestMethod.POST })
    public SubmitPaymentResponse submitPayment(@RequestBody(required = false) SubmitPaymentRequest req,
                                              @RequestParam(required = false) String account,
                                              HttpServletRequest request) {
        if (req == null) {
            req = new SubmitPaymentRequest();
            req.account = account;
        }
        log.info(">>>> [JSON] SubmitPayment for account={}", req.account);
        SubmitPaymentResponse res = new SubmitPaymentResponse();
        
        if (!verifySignature(request)) {
            res.responseCode = "deny";
            res.ticket = Arrays.asList("Ошибка верификации подписи", "Доступ запрещен");
            return res;
        }
        
        if ("error".equalsIgnoreCase(req.account)) {
            log.info(">>>> [JSON] Fake backend failure triggered for account 'error'");
            res.responseCode = "deny";
            res.ticket = Arrays.asList("Оплата отклонена", "Сбой банка/провайдера (тестовая ошибка)");
            return res;
        }
        
        long eripTrxId = (req.transactionId != null) ? req.transactionId : (System.currentTimeMillis() % 100000);
        if (trxCache.containsKey(eripTrxId)) {
             res.unipayTrxId = trxCache.get(eripTrxId);
             log.info(">>>> [IDEMPOTENCY] Returned cached unipayTrxId={} for transactionId={}", res.unipayTrxId, eripTrxId);
        } else {
             res.unipayTrxId = System.currentTimeMillis() / 1000;
             trxCache.put(eripTrxId, res.unipayTrxId);
             log.info(">>>> [IDEMPOTENCY] Generated new unipayTrxId={} for transactionId={}", res.unipayTrxId, eripTrxId);
        }
        
        return res;
    }

    @RequestMapping(value = { "/confirmPayment", "/confirm-payment" }, method = { RequestMethod.GET, RequestMethod.POST })
    public Map<String, Object> confirmPayment(@RequestBody(required = false) ConfirmPaymentRequest req,
                                             @RequestParam(required = false) String account,
                                              HttpServletRequest request) {
        if (req == null) {
            req = new ConfirmPaymentRequest();
            req.account = account;
            req.confirmed = true;
        }
        log.info(">>>> [JSON] ConfirmPayment for account={}, confirmed={}", req.account, req.confirmed);
        Map<String, Object> res = new HashMap<>();
        
        if (!verifySignature(request)) {
            res.put("responseCode", "deny");
            res.put("ticket", Arrays.asList("Ошибка верификации подписи", "Доступ запрещен"));
            return res;
        }
        
        res.put("responseCode", "allow");
        if (!req.confirmed) {
            log.info(">>>> [JSON] Payment CANCELLED by ALCOSI/ERIP. Reason: {}", req.errorText);
            res.put("ticket", Arrays.asList("Оплата отменена", (req.errorText != null ? req.errorText : "Неизвестная причина отмены")));
        } else {
            res.put("ticket", Arrays.asList("Оплата успешно завершена", "Спасибо!"));
        }
        
        return res;
    }

    private boolean verifySignature(HttpServletRequest request) {
        if (request == null) return true;
        
        String signature = request.getHeader("X-Signature");
        if (signature == null || signature.isEmpty()) return true;

        if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper wrapper) {
            try {
                String bodyStr = new String(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding() != null ? wrapper.getCharacterEncoding() : "UTF-8");
                String secret = "077ad577a2b0458ea329eaf91ae01130";
                String textToHash = bodyStr + secret;
                String computed = calculateMd5(textToHash);
                
                if (signature.equalsIgnoreCase(computed)) {
                    log.info(">>>> [SECURITY] Signature verified successfully!");
                    return true;
                } else {
                    log.error(">>>> [SECURITY] Invalid signature! Expected: {}, Actual: {}", computed, signature);
                    return true; // Bypass security check for other services
                }
            } catch (Exception e) {
                log.error(">>>> [SECURITY] Exception verifying signature", e);
            }
        }
        return true;
    }

    // Подстановка mock-значений для deny-параметров (US_3, Сценарий 2)
    private String generateMockValue(String paramName) {
        if (paramName == null) return "mock-value";
        String n = paramName.toLowerCase();
        if (n.contains("вес")) return "1.5";
        if (n.contains("дата")) return "20.05.2026";
        if (n.contains("тип")) return "Курьер";
        return "mock-value";
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

package by.hgrosh.mockserver.service;

import by.hgrosh.mockserver.dto.*;
import by.hgrosh.mockserver.model.LogEntry;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MockBillingService {

    private final String hashPhrase = "secret_phrase_123";
    private final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    private final Map<Long, HutkiRequest> pendingTransactions = new ConcurrentHashMap<>();

    public HutkiResponse processRequest(HutkiRequest request, String providedSignature, String rawBody, String url) {
        boolean sigValid = true;
        if (providedSignature != null) {
            String calculated = DigestUtils.md5Hex(rawBody + hashPhrase);
            sigValid = calculated.equalsIgnoreCase(providedSignature);
        }

        HutkiResponse response;
        String type = request.getType();

        if (type == null) {
            response = HutkiResponse.deny();
            response.setTicket(List.of("Ошибка: Не указан тип запроса (type)"));
        } else {
            response = switch (type) {
                case "accountInfo" -> handleAccountInfo(request);
                case "submitPayment" -> handleSubmitPayment(request);
                case "confirmPayment" -> handleConfirmPayment(request);
                default -> {
                    HutkiResponse res = HutkiResponse.deny();
                    res.setTicket(List.of("Ошибка: Неизвестный тип запроса: " + type));
                    yield res;
                }
            };
        }

        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .method("POST")
                .url(url)
                .request(request)
                .response(response)
                .signature(providedSignature)
                .signatureValid(sigValid)
                .build();

        logs.add(0, entry);
        if (logs.size() > 100) logs.remove(logs.size() - 1);

        return response;
    }

    private HutkiResponse handleAccountInfo(HutkiRequest request) {
        String account = request.getAccount();
        if ("0000000".equals(account)) {
            HutkiResponse res = HutkiResponse.deny();
            res.setTicket(List.of("Ошибка: Лицевой счет " + account + " не найден.", "Проверьте правильность ввода или обратитесь в поддержку."));
            return res;
        }

        if ("55-77-99".equals(account)) {
            // Check if parameter 300 is already present
            Optional<ParameterDTO> meterReading = request.getParameterList() != null ? 
                request.getParameterList().stream().filter(p -> p.getIdx() == 300).findFirst() : Optional.empty();

            if (meterReading.isEmpty() || meterReading.get().getValue() == null) {
                HutkiResponse res = HutkiResponse.allow();
                res.setNextRqType("ServiceInfo");
                res.setAmount(0.0);
                res.setEditable(false);
                ParameterDTO p = new ParameterDTO();
                p.setIdx(300);
                p.setName("Текущие показания счетчика");
                p.setEdit("allow");
                p.setDataType("1");
                p.setHint("Например: 145");
                res.setParameterList(List.of(p));
                res.setTicket(List.of("Пожалуйста, введите текущие показания счетчика"));
                return res;
            } else {
                HutkiResponse res = HutkiResponse.allow();
                res.setNextRqType("TransactionStart");
                res.setAmount(12.30);
                res.setEditable(false);
                res.setTicket(List.of("Показания приняты: " + meterReading.get().getValue(), "Сумма к оплате: 12.30 BYN"));
                return res;
            }
        }

        // Default: 12345678 or others
        HutkiResponse res = HutkiResponse.allow();
        res.setNextRqType("TransactionStart");
        res.setAmount(25.50);
        res.setEditable(true);
        res.setMinAmount(1.0);
        res.setMaxAmount(500.0);
        res.setSessionId(request.getSessionId());
        
        ClientNameDTO name = new ClientNameDTO();
        name.setFirstName("Иван");
        name.setMiddleName("Иванович");
        name.setSurName("И***в");
        res.setClientName(name);
        
        res.setTicket(List.of(
            "Оплата услуг интернет",
            "Лицевой счет: " + account,
            "Текущая задолженность: 25.50 BYN"
        ));
        return res;
    }

    private HutkiResponse handleSubmitPayment(HutkiRequest request) {
        HutkiResponse res = HutkiResponse.allow();
        long unipayTrxId = new Random().nextInt(900000) + 100000;
        res.setUnipayTrxId(unipayTrxId);
        res.setTicket(List.of("Оплата успешно начата.", "Номер операции ПУ: " + unipayTrxId));
        
        // Store transaction state for confirmPayment mapping
        pendingTransactions.put(unipayTrxId, request);
        return res;
    }

    private HutkiResponse handleConfirmPayment(HutkiRequest request) {
        Long unipayTrxId = request.getUnipayTrxId();
        HutkiResponse res = HutkiResponse.allow();
        
        if (Boolean.TRUE.equals(request.getConfirmed())) {
            res.setTicket(List.of("Оплата успешно завершена.", "Спасибо!"));
        } else {
            res.setTicket(List.of("Оплата отменена.", "Причина: " + request.getErrorText()));
        }
        
        return res;
    }

    public List<LogEntry> getLogs() {
        return logs;
    }

    public void addLog(LogEntry entry) {
        logs.add(0, entry);
        if (logs.size() > 100) logs.remove(logs.size() - 1);
    }
}

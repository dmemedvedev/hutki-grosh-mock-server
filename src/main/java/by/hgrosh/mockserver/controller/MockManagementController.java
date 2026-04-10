package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.model.DataStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class MockManagementController {

    @GetMapping("/logs-xml")
    public List<String> getXmlLogs() {
        return DataStore.xmlLogs;
    }

    @GetMapping("/logs-json")
    public List<String> getJsonLogs() {
        return DataStore.jsonLogs;
    }

    @GetMapping(value = { "/register-invoice", "/api/register-invoice", "/erip/register-invoice" })
    public String registerInvoice(
            @RequestParam String account,
            @RequestParam String amount,
            @RequestParam(defaultValue = "Medvedev") String surname,
            @RequestParam(defaultValue = "Dmitry") String firstName) {
        
        // Нормализуем сумму (для внутреннего хранения)
        String normAmount = amount.replace(",", ".");
        
        DataStore.invoiceStore.put(account, new DataStore.Invoice(account, normAmount, surname, firstName));
        return "OK: Invoice " + account + " registered for " + normAmount + " BYN (Accessible via JSON API)";
    }
}

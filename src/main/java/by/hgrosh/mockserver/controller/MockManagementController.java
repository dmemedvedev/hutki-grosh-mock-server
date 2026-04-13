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
            @RequestParam(defaultValue = "Dmitry") String firstName,
            @RequestParam(required = false) String requiredParams) {
        
        String normAmount = amount.replace(",", ".");
        DataStore.Invoice inv = new DataStore.Invoice(account, normAmount, surname, firstName);
        
        if (requiredParams != null && !requiredParams.isEmpty()) {
            String[] params = requiredParams.split(",");
            for (String p : params) {
                inv.requiredParameters.add(new DataStore.Parameter(p.trim(), "Параметр " + p.trim(), "string", true));
            }
        }
        
        DataStore.invoiceStore.put(account, inv);
        return "OK: Invoice " + account + " registered for " + normAmount + " BYN. Required params: " + 
               (requiredParams != null ? requiredParams : "none");
    }
}

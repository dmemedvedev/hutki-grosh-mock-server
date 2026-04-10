package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.service.MockBillingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final MockBillingService billingService;

    public DashboardController(MockBillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("logs", billingService.getLogs());
        model.addAttribute("invoices", by.hgrosh.mockserver.model.DataStore.invoiceStore.values());
        return "dashboard";
    }
}

package by.hgrosh.mockserver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    public static final long SERVICE_ID = 13381001L;
    public static final List<String> xmlLogs = java.util.Collections.synchronizedList(new ArrayList<>());
    public static final List<String> jsonLogs = java.util.Collections.synchronizedList(new ArrayList<>());
    public static final Map<String, Invoice> invoiceStore = new ConcurrentHashMap<>();
    public static final List<Operation> publicOperations = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public static class Operation {
        public String account;
        public String type;
        public double amount;
        public java.time.LocalDateTime timestamp;
        public boolean isVisible;

        public Operation(String account, String type, double amount, boolean isVisible) {
            this.account = account;
            this.type = type;
            this.amount = amount;
            this.isVisible = isVisible;
            this.timestamp = java.time.LocalDateTime.now();
        }
    }

    public static class Invoice {
        public String account;
        public String amount;
        public String surname;
        public String firstName;
        public String status = "Pending";
        public List<Operation> history = new java.util.ArrayList<>();

        public Invoice(String account, String amount, String surname, String firstName) {
            this.account = account;
            this.amount = amount;
            this.surname = surname;
            this.firstName = firstName;
        }
        
        // ... rest of methods
            try {
                return Double.parseDouble(this.amount.replace(",", "."));
            } catch (Exception e) {
                return 40.0;
            }
        }
        
        public String getAmountWithComma() {
            return this.amount.replace(".", ",");
        }
        
        public String getAmountWithDot() {
            return this.amount.replace(",", ".");
        }
    }
    
    public static void logXml(String entry) {
        xmlLogs.add(0, entry);
        if (xmlLogs.size() > 50) xmlLogs.remove(xmlLogs.size() - 1);
    }
    
    public static void logJson(String entry) {
        jsonLogs.add(0, entry);
        if (jsonLogs.size() > 50) jsonLogs.remove(jsonLogs.size() - 1);
    }
}

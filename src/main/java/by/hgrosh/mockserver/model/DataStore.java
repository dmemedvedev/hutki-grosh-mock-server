package by.hgrosh.mockserver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    public static final long SERVICE_ID = 12880001L;
    public static final List<String> xmlLogs = java.util.Collections.synchronizedList(new ArrayList<>());
    public static final List<String> jsonLogs = java.util.Collections.synchronizedList(new ArrayList<>());
    public static final Map<String, Invoice> invoiceStore = new ConcurrentHashMap<>();

    static {
        // Тестовые счета для новой услуги 12880001
        invoiceStore.put("test1126", new Invoice("test1126", "45,00", "Medvedev", "Dmitry"));
        invoiceStore.put("20587001", new Invoice("20587001", "100,50", "Ivanov", "Ivan"));
        invoiceStore.put("100", new Invoice("100", "1,00", "Test", "User"));
    }

    public static class Invoice {
        public String account;
        public String amount;
        public String surname;
        public String firstName;

        public Invoice(String account, String amount, String surname, String firstName) {
            this.account = account;
            this.amount = amount;
            this.surname = surname;
            this.firstName = firstName;
        }

        public double getAmountAsDouble() {
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

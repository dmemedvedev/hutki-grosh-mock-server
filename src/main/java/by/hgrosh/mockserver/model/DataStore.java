package by.hgrosh.mockserver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    public static final long SERVICE_ID = 13381001L;
    public static final List<String> xmlLogs = new ArrayList<>();
    public static final List<String> jsonLogs = new ArrayList<>();
    public static final Map<String, Invoice> invoiceStore = new ConcurrentHashMap<>();

    public static class Invoice {
        public String account;
        public String amount; // С запятой для XML, с точкой для JSON (будем нормализовать)
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

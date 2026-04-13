package by.hgrosh.mockserver.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {
    private static final Logger log = LoggerFactory.getLogger(DataStore.class);

    // Default SERVICE_ID for integration tests
    public static final long SERVICE_ID = 12880001L;
    
    // Secret phrase for MD5 signing (as per US_1)
    public static final String HASH_PHRASE = "SECRET_TOKEN_123";

    public static class Parameter {
        public String id;
        public String label;
        public String value;
        public String type; // e.g. "string", "number"
        public boolean required;

        public Parameter(String id, String label, String type, boolean required) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.required = required;
        }
    }

    public static class Invoice {
        public String account;
        public String amount;
        public String surname;
        public String firstName;
        public boolean isExternalRouting = true;
        
        // Multip-step parameters gathering (as per US_3)
        public List<Parameter> requiredParameters = new ArrayList<>();
        public Map<String, String> receivedParameters = new HashMap<>();

        public Invoice(String account, String amount, String surname, String firstName) {
            this.account = account;
            this.amount = amount;
            this.surname = surname;
            this.firstName = firstName;
        }
    }

    public static final Map<String, Invoice> invoiceStore = new ConcurrentHashMap<>();
    public static final List<String> xmlLogs = Collections.synchronizedList(new ArrayList<>());
    public static final List<String> jsonLogs = Collections.synchronizedList(new ArrayList<>());

    static {
        // Pre-populate some test accounts
        invoiceStore.put("test1", new Invoice("test1", "50.0", "Ivanov", "Ivan"));
        invoiceStore.put("test2", new Invoice("test2", "150.0", "Petrov", "Petr"));
        invoiceStore.put("testnew", new Invoice("testnew", "10.0", "New", "User"));
        
        // Special account with multi-step parameters (for testing US_3)
        Invoice multistep = new Invoice("multistep", "77.7", "Step", "Master");
        multistep.requiredParameters.add(new Parameter("counter_reading", "Показания счетчика", "number", true));
        multistep.requiredParameters.add(new Parameter("comment", "Комментарий", "string", false));
        invoiceStore.put("multistep", multistep);
    }

    public static void logXml(String xml) {
        log.info(">>>> [LOG-XML]: {}", xml);
        if (xmlLogs.size() > 100) xmlLogs.remove(0);
        xmlLogs.add(xml);
    }

    public static void logJson(String json) {
        log.info(">>>> [LOG-JSON]: {}", json);
        if (jsonLogs.size() > 100) jsonLogs.remove(0);
        jsonLogs.add(json);
    }
}

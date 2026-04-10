import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Test {
    public static void main(String[] args) {
        String requestId = "657";
        String serviceNo = "13381001";
        String account = "12345678";
        String sessionId = "1712586577000"; // Test Session ID
        String sessionXml = "<SessionId>" + sessionId + "</SessionId>";
        
        String payAmountXml = ""; // First request has no PayAmount
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"));
        String now = sdf.format(new Date());

        String outXml = "<?xml version=\"1.0\" encoding=\"WINDOWS-1251\" standalone=\"yes\"?>" +
                "<ServiceProvider_Response>" +
                "<Version>1</Version>" +
                "<RequestId>" + requestId + "</RequestId>" +
                "<Status>0</Status>" +
                "<DateTime>" + now + "</DateTime>" +
                sessionXml +
                "<ServiceNo>" + serviceNo + "</ServiceNo>" +
                "<PersonalAccount>" + account + "</PersonalAccount>" +
                "<Currency>933</Currency>" +
                "<ResponseType>ServiceInfo</ResponseType>" +
                "<ServiceInfo>" +
                "<Amount Editable=\"Y\" MinAmount=\"0,01\" MaxAmount=\"999999,99\">" +
                "<Debt>40,00</Debt>" +
                "<Penalty>0,00</Penalty>" +
                payAmountXml +
                "</Amount>" +
                "<Name>" +
                "<Surname>Медведев</Surname>" +
                "<FirstName>Дмитрий</FirstName>" +
                "<Patronymic>Эдуардович</Patronymic>" +
                "</Name>" +
                "<Address><City>Минск</City><Street>Скрыганова</Street><House>6</House></Address>" +
                "<Info><InfoLine>Счёт найден</InfoLine></Info>" +
                "</ServiceInfo>" +
                "</ServiceProvider_Response>";
                
        System.out.println(outXml);
    }
}

package by.hgrosh.mockserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HutkiResponse {
    private String responseCode; // "allow", "deny"
    private String nextRqType; // "ServiceInfo", "TransactionStart"
    private List<String> ticket;
    private String sessionId;
    private Double amount;
    private Boolean editable;
    private Double minAmount;
    private Double maxAmount;
    private String editFIO; // "allow", "deny"
    private ClientNameDTO clientName;
    private String editAddress; // "allow", "deny"
    private ClientAddressDTO address;
    private List<ParameterDTO> parameterList;

    // submitPayment field
    private Long unipayTrxId;

    public static HutkiResponse allow() {
        HutkiResponse response = new HutkiResponse();
        response.setResponseCode("allow");
        return response;
    }

    public static HutkiResponse deny() {
        HutkiResponse response = new HutkiResponse();
        response.setResponseCode("deny");
        return response;
    }
}

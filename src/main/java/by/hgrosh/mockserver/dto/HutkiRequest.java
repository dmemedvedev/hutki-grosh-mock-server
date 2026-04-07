package by.hgrosh.mockserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HutkiRequest {
    private String type;
    private Long serviceId;
    private String account;
    private String sessionId;
    private Integer raCode;
    private List<ParameterDTO> parameterList;

    // submitPayment fields
    private Double amount;
    private Long curAmount;
    private Double exRate;
    private Double amountBYR;
    private Long transactionId;
    private String authType;
    private String clientFIO;
    private ClientNameDTO clientName;
    private String clientAddress;
    private ClientAddressDTO address;
    private String billId;

    // confirmPayment fields
    private Long billNumber;
    private Long esasTransactionId;
    private Boolean confirmed;
    private Long unipayTrxId;
    private String errorText;
}

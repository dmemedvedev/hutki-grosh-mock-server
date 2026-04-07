package by.hgrosh.mockserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParameterDTO {
    private Integer idx;
    private String name;
    private String edit; // "allow", "deny"
    private String value;
    private String dataType; // "0", "1", "2", "3", "5"
    private String dataFormat;
    private Integer minLength;
    private Integer maxLength;
    private String hint;
}

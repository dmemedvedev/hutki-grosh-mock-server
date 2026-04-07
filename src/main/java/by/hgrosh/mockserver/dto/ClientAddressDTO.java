package by.hgrosh.mockserver.dto;

import lombok.Data;

@Data
public class ClientAddressDTO {
    private String city;
    private String street;
    private String house;
    private String building;
    private String apartment;
}

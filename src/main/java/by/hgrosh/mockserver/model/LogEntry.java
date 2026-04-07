package by.hgrosh.mockserver.model;

import by.hgrosh.mockserver.dto.HutkiRequest;
import by.hgrosh.mockserver.dto.HutkiResponse;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class LogEntry {
    private String id;
    private LocalDateTime timestamp;
    private String method;
    private String url;
    private HutkiRequest request;
    private HutkiResponse response;
    private String signature;
    private boolean signatureValid;
}

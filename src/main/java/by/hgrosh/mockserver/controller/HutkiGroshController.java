package by.hgrosh.mockserver.controller;

import by.hgrosh.mockserver.dto.HutkiRequest;
import by.hgrosh.mockserver.dto.HutkiResponse;
import by.hgrosh.mockserver.service.MockBillingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/")
public class HutkiGroshController {

    private final MockBillingService billingService;
    private final ObjectMapper objectMapper;

    public HutkiGroshController(MockBillingService billingService, ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"", "/info", "/submit", "/commit"})
    public HutkiResponse handleRequest(
            HttpServletRequest request,
            @RequestHeader(value = "DigestUtils.md5Hex", required = false) String signature
    ) throws IOException {
        String bodyString = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        HutkiRequest hutkiRequest = objectMapper.readValue(bodyString, HutkiRequest.class);
        
        return billingService.processRequest(hutkiRequest, signature, bodyString, request.getRequestURI());
    }
}

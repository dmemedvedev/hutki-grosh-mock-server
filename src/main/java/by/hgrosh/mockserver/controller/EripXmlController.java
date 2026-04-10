package by.hgrosh.mockserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles ERIP XML protocol requests from Hutki Grosh system.
 * Supports ServiceInfo (search) and Pay (payment) types.
 */
@RestController
@CrossOrigin(origins = "*")
public class EripXmlController {

    private static final Logger log = LoggerFactory.getLogger(EripXmlController.class);

    @PostMapping(value = { "", "/", "/erip", "/api/erip" }, consumes = { "application/x-www-form-urlencoded",
            "multipart/form-data", "*/*" })
    public void handleEripRequest(HttpServletRequest request, HttpServletResponse response) {
        log.info("XML Request received, but this logic is currently DISABLED (Migrating to JSON)");
        /* 
           Legacy XML handling code is preserved in git history or commented out here 
           if needed for reference.
        */
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}

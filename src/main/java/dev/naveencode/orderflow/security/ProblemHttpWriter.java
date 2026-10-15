package dev.naveencode.orderflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.naveencode.orderflow.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ProblemHttpWriter {

    private final ObjectMapper objectMapper;

    public ProblemHttpWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail) {
        if (response == null || response.isCommitted()) {
            return;
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("type", code.type());
        body.put("title", code.title());
        body.put("status", code.status().value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code.name());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            objectMapper.writeValue(response.getWriter(), body);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write error response", exception);
        }
    }
}

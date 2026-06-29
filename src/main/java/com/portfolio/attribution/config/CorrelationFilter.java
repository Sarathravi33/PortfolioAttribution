package com.portfolio.attribution.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public CorrelationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader("X-Request-ID");

        // Wrap early so the body can be read by both this filter and the controller
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = extractRequestIdFromBody(wrapped);
        }

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", correlationId);
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            MDC.clear();
        }
    }

    // Reads the cached body (populated after doFilter) to extract "requestId".
    // Called only as a pre-filter best-effort peek using the buffered wrapper content.
    private String extractRequestIdFromBody(ContentCachingRequestWrapper request) {
        try {
            byte[] body = request.getContentAsByteArray();
            if (body.length == 0) {
                return null;
            }
            JsonNode node = objectMapper.readTree(body);
            JsonNode idNode = node.get("requestId");
            return (idNode != null && !idNode.isNull()) ? idNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.portfolio.attribution.controller;

import com.portfolio.attribution.model.request.AttributionRequest;
import com.portfolio.attribution.model.response.AttributionResponse;
import com.portfolio.attribution.model.response.AttributionStatus;
import com.portfolio.attribution.service.AttributionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/performance")
public class AttributionController {

    private static final Logger log = LoggerFactory.getLogger(AttributionController.class);

    private final AttributionService attributionService;

    public AttributionController(AttributionService attributionService) {
        this.attributionService = attributionService;
    }

    @PostMapping("/attribution")
    public ResponseEntity<AttributionResponse> calculate(@Valid @RequestBody AttributionRequest request) {
        MDC.put("requestId", request.getRequestId());
        try {
            log.info("Attribution request received: requestId={}, portfolioId={}",
                    request.getRequestId(), request.getPortfolioId());

            AttributionResponse response = attributionService.calculate(request);

            log.info("Attribution response: requestId={}, status={}, degraded={}",
                    request.getRequestId(), response.getStatus(), response.isDegraded());

            if (response.getStatus() == AttributionStatus.INVALID_INPUT) {
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("requestId");
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("status", "INVALID_INPUT");
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }
}

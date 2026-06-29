package com.portfolio.attribution.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "attribution_results")
public class AttributionResultEntity {

    @Id
    @Column(name = "request_id", length = 100)
    private String requestId;

    @Lob
    @Column(name = "response_json", columnDefinition = "CLOB")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AttributionResultEntity() {}

    public AttributionResultEntity(String requestId, String responseJson, Instant createdAt) {
        this.requestId = requestId;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public String getRequestId() { return requestId; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}

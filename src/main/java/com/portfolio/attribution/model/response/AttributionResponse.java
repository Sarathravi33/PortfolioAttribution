package com.portfolio.attribution.model.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class AttributionResponse {

    private String requestId;
    private String portfolioId;
    private LocalDate valuationDate;
    private BigDecimal totalContributionPct;
    private List<GroupContribution> groupContributions;
    private AttributionStatus status;
    private boolean degraded;
    private List<String> warnings;
    private Instant processedAt;

    private AttributionResponse() {}

    public static Builder builder() { return new Builder(); }

    public String getRequestId() { return requestId; }
    public String getPortfolioId() { return portfolioId; }
    public LocalDate getValuationDate() { return valuationDate; }
    public BigDecimal getTotalContributionPct() { return totalContributionPct; }
    public List<GroupContribution> getGroupContributions() { return groupContributions; }
    public AttributionStatus getStatus() { return status; }
    public boolean isDegraded() { return degraded; }
    public List<String> getWarnings() { return warnings; }
    public Instant getProcessedAt() { return processedAt; }

    public static class Builder {
        private final AttributionResponse obj = new AttributionResponse();

        public Builder requestId(String v) { obj.requestId = v; return this; }
        public Builder portfolioId(String v) { obj.portfolioId = v; return this; }
        public Builder valuationDate(LocalDate v) { obj.valuationDate = v; return this; }
        public Builder totalContributionPct(BigDecimal v) { obj.totalContributionPct = v; return this; }
        public Builder groupContributions(List<GroupContribution> v) { obj.groupContributions = v; return this; }
        public Builder status(AttributionStatus v) { obj.status = v; return this; }
        public Builder degraded(boolean v) { obj.degraded = v; return this; }
        public Builder warnings(List<String> v) { obj.warnings = v; return this; }
        public Builder processedAt(Instant v) { obj.processedAt = v; return this; }
        public AttributionResponse build() { return obj; }
    }
}

package com.portfolio.attribution.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public class AttributionRequest {

    @NotBlank(message = "requestId is required")
    private String requestId;

    @NotBlank(message = "portfolioId is required")
    private String portfolioId;

    @NotNull(message = "valuationDate is required")
    private LocalDate valuationDate;

    @NotEmpty(message = "groups must not be empty")
    @Valid
    private List<GroupInput> groups;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "requestedBy is required")
    private String requestedBy;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }

    public LocalDate getValuationDate() { return valuationDate; }
    public void setValuationDate(LocalDate valuationDate) { this.valuationDate = valuationDate; }

    public List<GroupInput> getGroups() { return groups; }
    public void setGroups(List<GroupInput> groups) { this.groups = groups; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
}

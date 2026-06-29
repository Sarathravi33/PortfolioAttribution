package com.portfolio.attribution.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class GroupInput {

    @NotBlank(message = "groupName is required")
    private String groupName;

    @NotNull(message = "weightPct is required")
    @Positive(message = "weightPct must be positive")
    private BigDecimal weightPct;

    private BigDecimal returnPct;
    private BigDecimal fallbackReturnPct;

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public BigDecimal getWeightPct() { return weightPct; }
    public void setWeightPct(BigDecimal weightPct) { this.weightPct = weightPct; }

    public BigDecimal getReturnPct() { return returnPct; }
    public void setReturnPct(BigDecimal returnPct) { this.returnPct = returnPct; }

    public BigDecimal getFallbackReturnPct() { return fallbackReturnPct; }
    public void setFallbackReturnPct(BigDecimal fallbackReturnPct) { this.fallbackReturnPct = fallbackReturnPct; }
}

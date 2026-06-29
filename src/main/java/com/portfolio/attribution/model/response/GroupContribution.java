package com.portfolio.attribution.model.response;

import java.math.BigDecimal;

public class GroupContribution {

    private String groupName;
    private BigDecimal contributionPct;
    private PricingMode pricingMode;

    private GroupContribution() {}

    public static Builder builder() { return new Builder(); }

    public String getGroupName() { return groupName; }
    public BigDecimal getContributionPct() { return contributionPct; }
    public PricingMode getPricingMode() { return pricingMode; }

    public static class Builder {
        private final GroupContribution obj = new GroupContribution();

        public Builder groupName(String v) { obj.groupName = v; return this; }
        public Builder contributionPct(BigDecimal v) { obj.contributionPct = v; return this; }
        public Builder pricingMode(PricingMode v) { obj.pricingMode = v; return this; }
        public GroupContribution build() { return obj; }
    }
}

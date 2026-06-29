package com.portfolio.attribution.service;

import com.portfolio.attribution.model.request.AttributionRequest;
import com.portfolio.attribution.model.request.GroupInput;
import com.portfolio.attribution.model.response.AttributionResponse;
import com.portfolio.attribution.model.response.AttributionStatus;
import com.portfolio.attribution.model.response.GroupContribution;
import com.portfolio.attribution.model.response.PricingMode;
import com.portfolio.attribution.validation.AttributionRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AttributionService {

    private static final Logger log = LoggerFactory.getLogger(AttributionService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AttributionRequestValidator validator;

    public AttributionService(AttributionRequestValidator validator) {
        this.validator = validator;
    }

    public AttributionResponse calculate(AttributionRequest request) {
        Optional<String> weightError = validator.validate(request);
        if (weightError.isPresent()) {
            log.warn("Invalid weight for requestId={}: {}", request.getRequestId(), weightError.get());
            return AttributionResponse.builder()
                    .requestId(request.getRequestId())
                    .portfolioId(request.getPortfolioId())
                    .valuationDate(request.getValuationDate())
                    .totalContributionPct(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .groupContributions(List.of())
                    .status(AttributionStatus.INVALID_INPUT)
                    .degraded(false)
                    .warnings(List.of(weightError.get()))
                    .processedAt(Instant.now())
                    .build();
        }

        List<GroupContribution> contributions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> unresolvableGroups = new ArrayList<>();

        for (GroupInput group : request.getGroups()) {
            if (group.getReturnPct() != null) {
                BigDecimal contribution = group.getWeightPct()
                        .multiply(group.getReturnPct())
                        .divide(HUNDRED, 6, RoundingMode.HALF_UP);
                contributions.add(GroupContribution.builder()
                        .groupName(group.getGroupName())
                        .contributionPct(contribution)
                        .pricingMode(PricingMode.PRIMARY)
                        .build());

            } else if (group.getFallbackReturnPct() != null) {
                BigDecimal contribution = group.getWeightPct()
                        .multiply(group.getFallbackReturnPct())
                        .divide(HUNDRED, 6, RoundingMode.HALF_UP);
                contributions.add(GroupContribution.builder()
                        .groupName(group.getGroupName())
                        .contributionPct(contribution)
                        .pricingMode(PricingMode.FALLBACK_USED)
                        .build());
                warnings.add("Fallback pricing used for " + group.getGroupName());
                log.info("Fallback pricing applied for group={}, requestId={}",
                        group.getGroupName(), request.getRequestId());

            } else {
                unresolvableGroups.add(group.getGroupName());
                warnings.add("Return unavailable for " + group.getGroupName() + ", group excluded from total");
                log.warn("No return data for group={}, requestId={}", group.getGroupName(), request.getRequestId());
            }
        }

        AttributionStatus status;
        if (unresolvableGroups.isEmpty()) {
            status = AttributionStatus.VALID;
        } else if (unresolvableGroups.size() == 1) {
            status = AttributionStatus.DEGRADED;
        } else {
            status = AttributionStatus.REVIEW_REQUIRED;
        }

        boolean degraded = status == AttributionStatus.DEGRADED || status == AttributionStatus.REVIEW_REQUIRED;

        BigDecimal total = contributions.stream()
                .map(GroupContribution::getContributionPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(6, RoundingMode.HALF_UP);

        return AttributionResponse.builder()
                .requestId(request.getRequestId())
                .portfolioId(request.getPortfolioId())
                .valuationDate(request.getValuationDate())
                .totalContributionPct(total)
                .groupContributions(contributions)
                .status(status)
                .degraded(degraded)
                .warnings(warnings)
                .processedAt(Instant.now())
                .build();
    }
}

package com.portfolio.attribution.validation;

import com.portfolio.attribution.model.request.AttributionRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class AttributionRequestValidator {

    private static final BigDecimal MIN_WEIGHT = new BigDecimal("99.0");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("101.0");

    public Optional<String> validate(AttributionRequest request) {
        BigDecimal totalWeight = request.getGroups().stream()
                .map(g -> g.getWeightPct() != null ? g.getWeightPct() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(MIN_WEIGHT) < 0 || totalWeight.compareTo(MAX_WEIGHT) > 0) {
            return Optional.of(String.format(
                    "Total weight %.2f%% is outside the valid range [99%%, 101%%]", totalWeight));
        }
        return Optional.empty();
    }
}

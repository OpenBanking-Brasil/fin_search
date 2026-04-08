package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;

public record PatternInsight(
        String type,
        String title,
        String details,
        String severity,
        BigDecimal estimatedMonthlyImpact
) {
}

package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;

public record CashFlowForecast(
        BigDecimal projectedInflow30d,
        BigDecimal projectedOutflow30d,
        BigDecimal projectedNet30d,
        String riskLevel
) {
}

package br.aof.read_opendata_apis.financialassistant.application.dto;

import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record NormalizedEventInput(
        FinanceSource source,
        String externalId,
        String sourceRef,
        LocalDateTime occurredAt,
        String description,
        BigDecimal amount,
        String currency,
        FinanceEventType eventType,
        FinanceCategory category,
        String merchant,
        String counterparty,
        Map<String, Object> metadata
) {
}

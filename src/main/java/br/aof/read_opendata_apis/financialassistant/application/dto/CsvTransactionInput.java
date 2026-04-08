package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CsvTransactionInput(
        String lineId,
        String sourceRef,
        LocalDateTime occurredAt,
        String description,
        BigDecimal amount,
        String currency,
        String category,
        String merchant
) {
}

package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankTransactionInput(
        String transactionId,
        String accountRef,
        LocalDateTime occurredAt,
        String description,
        BigDecimal amount,
        String currency,
        String merchant,
        String counterparty
) {
}

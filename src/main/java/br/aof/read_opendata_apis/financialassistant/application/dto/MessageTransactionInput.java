package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MessageTransactionInput(
        String messageId,
        String channel,
        String sender,
        String text,
        BigDecimal amount,
        String currency,
        String merchant,
        LocalDateTime sentAt
) {
}

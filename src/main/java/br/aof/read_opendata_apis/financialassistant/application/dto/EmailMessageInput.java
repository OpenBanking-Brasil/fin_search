package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EmailMessageInput(
        String messageId,
        String sender,
        String subject,
        String body,
        BigDecimal amount,
        String currency,
        String merchant,
        LocalDateTime receivedAt
) {
}

package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OcrReceiptInput(
        String receiptId,
        String sourceRef,
        String rawText,
        BigDecimal totalAmount,
        String currency,
        String merchant,
        LocalDateTime purchasedAt
) {
}

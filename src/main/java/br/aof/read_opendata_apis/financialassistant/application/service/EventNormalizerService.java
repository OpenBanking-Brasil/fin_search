package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.NormalizedEventInput;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEvent;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
public class EventNormalizerService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FinanceEvent normalize(NormalizedEventInput input) {
        FinanceEvent event = new FinanceEvent();
        event.setSource(input.source() == null ? FinanceSource.MANUAL : input.source());
        event.setExternalId(clean(input.externalId()));
        event.setSourceRef(cleanOrDefault(input.sourceRef(), "unknown-source-ref"));
        event.setOccurredAt(input.occurredAt() == null ? LocalDateTime.now() : input.occurredAt());
        event.setDescription(cleanOrDefault(input.description(), "Transaction imported"));
        event.setAmount(input.amount() == null ? BigDecimal.ZERO : input.amount());
        event.setCurrency(cleanOrDefault(input.currency(), "BRL").toUpperCase(Locale.ROOT));
        event.setEventType(input.eventType() == null ? FinanceEventType.OTHER : input.eventType());
        event.setCategory(input.category() == null ? FinanceCategory.UNKNOWN : input.category());
        event.setMerchant(clean(input.merchant()));
        event.setCounterparty(clean(input.counterparty()));
        event.setMetadataJson(writeMetadataJson(input.metadata()));
        event.setFingerprint(buildFingerprint(event));
        return event;
    }

    private String writeMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            ObjectMapper sorted = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return sorted.writeValueAsString(metadata);
        } catch (JsonProcessingException ignored) {
            return "{}";
        }
    }

    private String buildFingerprint(FinanceEvent event) {
        String base = String.join("|",
                safe(event.getSource().name()),
                safe(event.getExternalId()),
                safe(event.getSourceRef()),
                safe(event.getOccurredAt().toString()),
                safe(event.getAmount().toPlainString()),
                safe(event.getCurrency()),
                safe(event.getDescription().toLowerCase(Locale.ROOT)),
                safe(event.getMerchant()),
                safe(event.getCounterparty())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanOrDefault(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned == null || cleaned.isBlank() ? fallback : cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

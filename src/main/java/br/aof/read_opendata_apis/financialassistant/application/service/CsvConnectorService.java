package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.CsvTransactionInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.NormalizedEventInput;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CsvConnectorService {

    private final EventIngestionService eventIngestionService;

    public IngestionResult ingest(List<CsvTransactionInput> rows) {
        List<NormalizedEventInput> normalized = rows.stream()
                .map(row -> new NormalizedEventInput(
                        FinanceSource.CSV_IMPORT,
                        row.lineId(),
                        row.sourceRef(),
                        row.occurredAt(),
                        row.description(),
                        row.amount(),
                        row.currency(),
                        row.amount() != null && row.amount().signum() >= 0 ? FinanceEventType.INCOME : FinanceEventType.EXPENSE,
                        parseCategory(row.category()),
                        row.merchant(),
                        null,
                        Map.of("source", "csv")
                ))
                .toList();
        return eventIngestionService.normalizeAndPersistBatch(normalized);
    }

    private FinanceCategory parseCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return FinanceCategory.UNKNOWN;
        }
        String normalized = rawCategory.trim().toUpperCase(Locale.ROOT);
        try {
            return FinanceCategory.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return FinanceCategory.UNKNOWN;
        }
    }
}

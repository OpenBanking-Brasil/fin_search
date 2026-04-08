package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.ManualEventInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.NormalizedEventInput;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualEntryConnectorService {

    private final EventIngestionService eventIngestionService;

    public IngestionResult ingest(List<ManualEventInput> entries) {
        List<NormalizedEventInput> normalized = entries.stream()
                .map(entry -> new NormalizedEventInput(
                        FinanceSource.MANUAL,
                        null,
                        entry.sourceRef(),
                        entry.occurredAt(),
                        entry.description(),
                        entry.amount(),
                        entry.currency(),
                        entry.eventType(),
                        entry.category(),
                        entry.merchant(),
                        entry.counterparty(),
                        entry.metadata()
                ))
                .toList();
        return eventIngestionService.normalizeAndPersistBatch(normalized);
    }
}

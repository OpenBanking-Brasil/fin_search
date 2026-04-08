package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.BankTransactionInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.NormalizedEventInput;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenFinanceConnectorService {

    private final EventIngestionService eventIngestionService;

    public IngestionResult ingest(List<BankTransactionInput> transactions) {
        List<NormalizedEventInput> normalized = transactions.stream()
                .map(tx -> new NormalizedEventInput(
                        FinanceSource.OPEN_FINANCE,
                        tx.transactionId(),
                        tx.accountRef(),
                        tx.occurredAt(),
                        tx.description(),
                        tx.amount(),
                        tx.currency(),
                        tx.amount() != null && tx.amount().signum() >= 0 ? FinanceEventType.INCOME : FinanceEventType.EXPENSE,
                        FinanceCategory.UNKNOWN,
                        tx.merchant(),
                        tx.counterparty(),
                        Map.of("source", "open_finance")
                ))
                .toList();
        return eventIngestionService.normalizeAndPersistBatch(normalized);
    }
}

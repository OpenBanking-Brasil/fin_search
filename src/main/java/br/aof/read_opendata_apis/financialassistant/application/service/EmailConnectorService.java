package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.EmailMessageInput;
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
public class EmailConnectorService {

    private final EventIngestionService eventIngestionService;

    public IngestionResult ingest(List<EmailMessageInput> emails) {
        List<NormalizedEventInput> normalized = emails.stream()
                .map(email -> new NormalizedEventInput(
                        FinanceSource.EMAIL,
                        email.messageId(),
                        "email:" + email.sender(),
                        email.receivedAt(),
                        email.subject(),
                        email.amount(),
                        email.currency(),
                        FinanceEventType.EXPENSE,
                        FinanceCategory.UNKNOWN,
                        email.merchant(),
                        email.sender(),
                        Map.of("body", email.body() == null ? "" : email.body())
                ))
                .toList();
        return eventIngestionService.normalizeAndPersistBatch(normalized);
    }
}

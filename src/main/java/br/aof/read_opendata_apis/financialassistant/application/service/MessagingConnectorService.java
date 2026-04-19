package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.MessageTransactionInput;
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
public class MessagingConnectorService {

    private final EventIngestionService eventIngestionService;

    public IngestionResult ingest(List<MessageTransactionInput> messages) {
        List<NormalizedEventInput> normalized = messages.stream()
                .map(message -> new NormalizedEventInput(
                        FinanceSource.MESSAGING,
                        message.messageId(),
                        "msg:" + (message.channel() == null ? "unknown" : message.channel()),
                        message.sentAt(),
                        message.text(),
                        message.amount(),
                        message.currency(),
                        FinanceEventType.EXPENSE,
                        FinanceCategory.UNKNOWN,
                        message.merchant(),
                        message.sender(),
                        Map.of("channel", message.channel() == null ? "unknown" : message.channel())
                ))
                .toList();
        return eventIngestionService.normalizeAndPersistBatch(normalized);
    }
}

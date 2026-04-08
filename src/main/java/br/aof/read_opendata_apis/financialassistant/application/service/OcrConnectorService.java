package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.NormalizedEventInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.OcrReceiptInput;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OcrConnectorService {

    private final EventIngestionService eventIngestionService;

    public IngestionResult ingest(List<OcrReceiptInput> receipts) {
        List<NormalizedEventInput> normalized = receipts.stream()
                .map(receipt -> new NormalizedEventInput(
                        FinanceSource.OCR_RECEIPT,
                        receipt.receiptId(),
                        receipt.sourceRef(),
                        receipt.purchasedAt(),
                        "OCR receipt import",
                        receipt.totalAmount(),
                        receipt.currency(),
                        FinanceEventType.EXPENSE,
                        FinanceCategory.SHOPPING,
                        receipt.merchant(),
                        null,
                        Map.of("rawText", receipt.rawText() == null ? "" : receipt.rawText())
                ))
                .toList();
        return eventIngestionService.normalizeAndPersistBatch(normalized);
    }
}

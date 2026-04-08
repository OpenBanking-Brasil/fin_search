package br.aof.read_opendata_apis.financialassistant.application.controller;

import br.aof.read_opendata_apis.financialassistant.application.dto.BankTransactionInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.CashFlowForecast;
import br.aof.read_opendata_apis.financialassistant.application.dto.ConsentRequest;
import br.aof.read_opendata_apis.financialassistant.application.dto.CryptoPayloadRequest;
import br.aof.read_opendata_apis.financialassistant.application.dto.CsvTransactionInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.EmailMessageInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.ManualEventInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.MessageTransactionInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.OcrReceiptInput;
import br.aof.read_opendata_apis.financialassistant.application.dto.PatternInsight;
import br.aof.read_opendata_apis.financialassistant.application.dto.RecommendationDecisionRequest;
import br.aof.read_opendata_apis.financialassistant.application.service.ApprovalWorkflowService;
import br.aof.read_opendata_apis.financialassistant.application.service.CashFlowForecastService;
import br.aof.read_opendata_apis.financialassistant.application.service.CategoryClassificationService;
import br.aof.read_opendata_apis.financialassistant.application.service.ConsentRegistryService;
import br.aof.read_opendata_apis.financialassistant.application.service.CsvConnectorService;
import br.aof.read_opendata_apis.financialassistant.application.service.DataCryptoService;
import br.aof.read_opendata_apis.financialassistant.application.service.EmailConnectorService;
import br.aof.read_opendata_apis.financialassistant.application.service.ManualEntryConnectorService;
import br.aof.read_opendata_apis.financialassistant.application.service.MessagingConnectorService;
import br.aof.read_opendata_apis.financialassistant.application.service.OcrConnectorService;
import br.aof.read_opendata_apis.financialassistant.application.service.OpenFinanceConnectorService;
import br.aof.read_opendata_apis.financialassistant.application.service.PatternEngineService;
import br.aof.read_opendata_apis.financialassistant.application.service.RecommendationEngineService;
import br.aof.read_opendata_apis.financialassistant.domain.model.ConsentRecord;
import br.aof.read_opendata_apis.financialassistant.domain.model.RecommendationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/financial-assistant", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class FinancialAssistantController {

    private final EmailConnectorService emailConnectorService;
    private final CsvConnectorService csvConnectorService;
    private final OcrConnectorService ocrConnectorService;
    private final ManualEntryConnectorService manualEntryConnectorService;
    private final MessagingConnectorService messagingConnectorService;
    private final OpenFinanceConnectorService openFinanceConnectorService;
    private final CategoryClassificationService categoryClassificationService;
    private final PatternEngineService patternEngineService;
    private final CashFlowForecastService cashFlowForecastService;
    private final RecommendationEngineService recommendationEngineService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final ConsentRegistryService consentRegistryService;
    private final DataCryptoService dataCryptoService;

    @PostMapping("/ingest/email")
    public IngestionResult ingestEmail(@RequestBody List<EmailMessageInput> payload) {
        return emailConnectorService.ingest(payload);
    }

    @PostMapping("/ingest/csv")
    public IngestionResult ingestCsv(@RequestBody List<CsvTransactionInput> payload) {
        return csvConnectorService.ingest(payload);
    }

    @PostMapping("/ingest/ocr")
    public IngestionResult ingestOcr(@RequestBody List<OcrReceiptInput> payload) {
        return ocrConnectorService.ingest(payload);
    }

    @PostMapping("/ingest/manual")
    public IngestionResult ingestManual(@RequestBody List<ManualEventInput> payload) {
        return manualEntryConnectorService.ingest(payload);
    }

    @PostMapping("/ingest/messages")
    public IngestionResult ingestMessages(@RequestBody List<MessageTransactionInput> payload) {
        return messagingConnectorService.ingest(payload);
    }

    @PostMapping("/ingest/open-finance")
    public IngestionResult ingestOpenFinance(@RequestBody List<BankTransactionInput> payload) {
        return openFinanceConnectorService.ingest(payload);
    }

    @PostMapping("/analysis/classify")
    public Map<String, Object> classifyUnknownCategories() {
        int updated = categoryClassificationService.classifyUnlabeledEvents();
        return Map.of("updated", updated);
    }

    @GetMapping("/analysis/patterns")
    public List<PatternInsight> detectPatterns() {
        return patternEngineService.detectPatterns();
    }

    @GetMapping("/analysis/forecast")
    public CashFlowForecast forecastCashflow() {
        return cashFlowForecastService.forecastNext30Days();
    }

    @PostMapping("/advisor/recommendations/generate")
    public List<RecommendationItem> generateRecommendations() {
        return recommendationEngineService.generateRecommendations();
    }

    @GetMapping("/advisor/recommendations/pending")
    public List<RecommendationItem> listPendingRecommendations() {
        return approvalWorkflowService.listPendingApprovals();
    }

    @PostMapping("/advisor/recommendations/{id}/decision")
    public RecommendationItem decideRecommendation(
            @PathVariable("id") Long id,
            @RequestBody RecommendationDecisionRequest request
    ) {
        return approvalWorkflowService.decide(id, request.approve(), request.note());
    }

    @PostMapping("/advisor/recommendations/{id}/execute")
    public RecommendationItem executeRecommendation(
            @PathVariable("id") Long id,
            @RequestBody RecommendationDecisionRequest request
    ) {
        return approvalWorkflowService.executeApproved(id, request.note());
    }

    @PostMapping("/security/consent/grant")
    public ConsentRecord grantConsent(@RequestBody ConsentRequest request) {
        return consentRegistryService.grant(request.subjectId(), request.source(), request.scope(), request.actor());
    }

    @PostMapping("/security/consent/revoke")
    public ConsentRecord revokeConsent(@RequestBody ConsentRequest request) {
        return consentRegistryService.revoke(request.subjectId(), request.source(), request.scope(), request.actor());
    }

    @GetMapping("/security/consent/check")
    public Map<String, Object> checkConsent(
            @RequestParam("subjectId") String subjectId,
            @RequestParam("source") String source,
            @RequestParam("scope") String scope
    ) {
        boolean granted = consentRegistryService.hasConsent(
                subjectId,
                br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource.valueOf(source.toUpperCase(Locale.ROOT)),
                scope
        );
        return Map.of("granted", granted);
    }

    @PostMapping("/security/encrypt")
    public Map<String, String> encrypt(@RequestBody CryptoPayloadRequest request) {
        return Map.of("encrypted", dataCryptoService.encrypt(request.value()));
    }

    @PostMapping("/security/decrypt")
    public Map<String, String> decrypt(@RequestBody CryptoPayloadRequest request) {
        return Map.of("decrypted", dataCryptoService.decrypt(request.value()));
    }
}

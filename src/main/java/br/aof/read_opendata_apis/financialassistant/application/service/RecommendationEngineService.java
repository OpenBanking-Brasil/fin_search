package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.CashFlowForecast;
import br.aof.read_opendata_apis.financialassistant.application.dto.PatternInsight;
import br.aof.read_opendata_apis.financialassistant.application.repository.RecommendationItemRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.RecommendationItem;
import br.aof.read_opendata_apis.financialassistant.domain.model.RecommendationPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationEngineService {

    private final PatternEngineService patternEngineService;
    private final CashFlowForecastService cashFlowForecastService;
    private final RecommendationItemRepository recommendationItemRepository;

    public List<RecommendationItem> generateRecommendations() {
        List<RecommendationItem> generated = new ArrayList<>();
        List<PatternInsight> insights = patternEngineService.detectPatterns();
        CashFlowForecast forecast = cashFlowForecastService.forecastNext30Days();

        for (PatternInsight insight : insights) {
            RecommendationItem item = new RecommendationItem();
            item.setTitle(insight.title());
            item.setReasoning(insight.details());
            item.setRecommendedAction(buildActionFromInsight(insight));
            item.setEstimatedImpact(insight.estimatedMonthlyImpact());
            item.setConfidence(confidenceForSeverity(insight.severity()));
            item.setPriority(priorityForSeverity(insight.severity()));
            generated.add(item);
        }

        if ("HIGH".equalsIgnoreCase(forecast.riskLevel())) {
            RecommendationItem safeguard = new RecommendationItem();
            safeguard.setTitle("Risco de caixa elevado nos proximos 30 dias");
            safeguard.setReasoning("A previsao indica saldo liquido negativo.");
            safeguard.setRecommendedAction("Priorizar corte de despesas variaveis e negociar contas recorrentes com maior impacto.");
            safeguard.setEstimatedImpact(forecast.projectedNet30d().abs());
            safeguard.setConfidence(new BigDecimal("0.78"));
            safeguard.setPriority(RecommendationPriority.HIGH);
            generated.add(safeguard);
        }

        return recommendationItemRepository.saveAll(generated);
    }

    private String buildActionFromInsight(PatternInsight insight) {
        if ("RECURRING_CHARGE".equalsIgnoreCase(insight.type())) {
            return "Revisar assinatura recorrente e cancelar ou renegociar se pouco utilizada.";
        }
        if ("SPENDING_SPIKE".equalsIgnoreCase(insight.type())) {
            return "Aplicar teto de gasto semanal na categoria com aceleracao e ativar alerta preventivo.";
        }
        return "Revisar a movimentacao e definir acao financeira preventiva.";
    }

    private RecommendationPriority priorityForSeverity(String severity) {
        if ("HIGH".equalsIgnoreCase(severity)) {
            return RecommendationPriority.HIGH;
        }
        if ("MEDIUM".equalsIgnoreCase(severity)) {
            return RecommendationPriority.MEDIUM;
        }
        return RecommendationPriority.LOW;
    }

    private BigDecimal confidenceForSeverity(String severity) {
        if ("HIGH".equalsIgnoreCase(severity)) {
            return new BigDecimal("0.85");
        }
        if ("MEDIUM".equalsIgnoreCase(severity)) {
            return new BigDecimal("0.70");
        }
        return new BigDecimal("0.55");
    }
}

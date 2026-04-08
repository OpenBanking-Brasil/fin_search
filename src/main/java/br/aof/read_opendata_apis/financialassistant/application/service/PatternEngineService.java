package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.PatternInsight;
import br.aof.read_opendata_apis.financialassistant.application.repository.FinanceEventRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEvent;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatternEngineService {

    private final FinanceEventRepository financeEventRepository;

    public List<PatternInsight> detectPatterns() {
        List<PatternInsight> insights = new ArrayList<>();
        insights.addAll(detectRecurringCharges());
        insights.addAll(detectSpendingSpikes());
        return insights;
    }

    private List<PatternInsight> detectRecurringCharges() {
        List<FinanceEvent> events = financeEventRepository.findTop500ByOrderByOccurredAtDesc();
        Map<String, List<FinanceEvent>> byMerchant = new HashMap<>();
        for (FinanceEvent event : events) {
            if (event.getEventType() != FinanceEventType.EXPENSE || event.getMerchant() == null || event.getMerchant().isBlank()) {
                continue;
            }
            byMerchant.computeIfAbsent(event.getMerchant().toLowerCase(), ignored -> new ArrayList<>()).add(event);
        }

        List<PatternInsight> recurring = new ArrayList<>();
        for (Map.Entry<String, List<FinanceEvent>> entry : byMerchant.entrySet()) {
            if (entry.getValue().size() < 3) {
                continue;
            }
            BigDecimal average = entry.getValue().stream()
                    .map(FinanceEvent::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(entry.getValue().size()), 2, java.math.RoundingMode.HALF_UP);
            recurring.add(new PatternInsight(
                    "RECURRING_CHARGE",
                    "Cobranca recorrente detectada em " + entry.getKey(),
                    "Foram encontradas " + entry.getValue().size() + " transacoes semelhantes.",
                    "MEDIUM",
                    average.abs()
            ));
        }
        return recurring;
    }

    private List<PatternInsight> detectSpendingSpikes() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal recent = sumExpenses(now.minusDays(30), now);
        BigDecimal previous = sumExpenses(now.minusDays(60), now.minusDays(30));
        if (previous.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal increaseRatio = recent.subtract(previous)
                .divide(previous, 2, java.math.RoundingMode.HALF_UP);

        if (increaseRatio.compareTo(new BigDecimal("0.25")) <= 0) {
            return List.of();
        }

        String severity = increaseRatio.compareTo(new BigDecimal("0.50")) > 0 ? "HIGH" : "MEDIUM";
        return List.of(new PatternInsight(
                "SPENDING_SPIKE",
                "Aumento relevante de gastos nos ultimos 30 dias",
                "Comparado com os 30 dias anteriores, houve variacao de " + increaseRatio.multiply(BigDecimal.valueOf(100)) + "%.",
                severity,
                recent.subtract(previous).abs()
        ));
    }

    private BigDecimal sumExpenses(LocalDateTime from, LocalDateTime to) {
        return financeEventRepository.findByOccurredAtBetween(from, to).stream()
                .filter(event -> event.getEventType() == FinanceEventType.EXPENSE)
                .map(FinanceEvent::getAmount)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.repository.FinanceEventRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryClassificationService {

    private static final Map<FinanceCategory, List<String>> KEYWORDS = Map.of(
            FinanceCategory.FOOD, List.of("ifood", "restaurante", "mercado", "supermercado", "padaria"),
            FinanceCategory.TRANSPORT, List.of("uber", "99", "combustivel", "posto", "estacionamento"),
            FinanceCategory.SUBSCRIPTIONS, List.of("spotify", "netflix", "prime", "assinatura", "youtube"),
            FinanceCategory.HEALTH, List.of("farmacia", "hospital", "consulta", "plano de saude"),
            FinanceCategory.HOUSING, List.of("aluguel", "condominio", "energia", "agua", "internet")
    );

    private final FinanceEventRepository financeEventRepository;

    public int classifyUnlabeledEvents() {
        List<FinanceEvent> candidates = financeEventRepository.findTop500ByCategoryOrderByOccurredAtDesc(FinanceCategory.UNKNOWN);
        int updated = 0;
        for (FinanceEvent event : candidates) {
            FinanceCategory predicted = inferCategory(event);
            if (predicted != FinanceCategory.UNKNOWN) {
                event.setCategory(predicted);
                updated++;
            }
        }
        if (!candidates.isEmpty()) {
            financeEventRepository.saveAll(candidates);
        }
        return updated;
    }

    private FinanceCategory inferCategory(FinanceEvent event) {
        String text = (safe(event.getDescription()) + " " + safe(event.getMerchant())).toLowerCase();
        for (Map.Entry<FinanceCategory, List<String>> entry : KEYWORDS.entrySet()) {
            boolean match = entry.getValue().stream().anyMatch(text::contains);
            if (match) {
                return entry.getKey();
            }
        }
        return FinanceCategory.UNKNOWN;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}

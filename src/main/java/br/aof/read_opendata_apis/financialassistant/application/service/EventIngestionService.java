package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.IngestionResult;
import br.aof.read_opendata_apis.financialassistant.application.dto.NormalizedEventInput;
import br.aof.read_opendata_apis.financialassistant.application.repository.FinanceEventRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final EventNormalizerService normalizerService;
    private final FinanceEventRepository financeEventRepository;

    public IngestionResult normalizeAndPersistBatch(List<NormalizedEventInput> rawInputs) {
        if (rawInputs == null || rawInputs.isEmpty()) {
            return new IngestionResult(0, 0, 0, List.of());
        }

        int persisted = 0;
        int duplicates = 0;
        List<String> skippedFingerprints = new ArrayList<>();

        for (NormalizedEventInput input : rawInputs) {
            FinanceEvent event = normalizerService.normalize(input);
            if (financeEventRepository.existsByFingerprint(event.getFingerprint())) {
                duplicates++;
                skippedFingerprints.add(event.getFingerprint());
                continue;
            }
            financeEventRepository.save(event);
            persisted++;
        }

        return new IngestionResult(rawInputs.size(), persisted, duplicates, skippedFingerprints);
    }
}

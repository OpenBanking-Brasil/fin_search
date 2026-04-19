package br.aof.read_opendata_apis.financialassistant.application.dto;

import java.util.List;

public record IngestionResult(
        int received,
        int persisted,
        int duplicates,
        List<String> skippedFingerprints
) {
}

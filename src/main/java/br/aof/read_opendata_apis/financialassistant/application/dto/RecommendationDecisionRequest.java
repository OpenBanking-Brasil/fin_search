package br.aof.read_opendata_apis.financialassistant.application.dto;

public record RecommendationDecisionRequest(
        boolean approve,
        String note
) {
}

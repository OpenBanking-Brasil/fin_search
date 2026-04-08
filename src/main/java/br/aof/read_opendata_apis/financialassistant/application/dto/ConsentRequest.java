package br.aof.read_opendata_apis.financialassistant.application.dto;

import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;

public record ConsentRequest(
        String subjectId,
        FinanceSource source,
        String scope,
        String actor
) {
}

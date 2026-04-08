package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.repository.ConsentRecordRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.ConsentRecord;
import br.aof.read_opendata_apis.financialassistant.domain.model.ConsentStatus;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConsentRegistryService {

    private final ConsentRecordRepository consentRecordRepository;
    private final SecurityAuditService securityAuditService;

    public ConsentRecord grant(String subjectId, FinanceSource source, String scope, String actor) {
        ConsentRecord record = consentRecordRepository
                .findBySubjectIdAndSourceAndScope(subjectId, source, scope)
                .orElseGet(ConsentRecord::new);
        record.setSubjectId(subjectId);
        record.setSource(source);
        record.setScope(scope);
        record.setStatus(ConsentStatus.GRANTED);
        record.setUpdatedAt(LocalDateTime.now());
        ConsentRecord saved = consentRecordRepository.save(record);
        securityAuditService.register("CONSENT_GRANTED", actor, "subjectId=" + subjectId + ",source=" + source + ",scope=" + scope);
        return saved;
    }

    public ConsentRecord revoke(String subjectId, FinanceSource source, String scope, String actor) {
        ConsentRecord record = consentRecordRepository
                .findBySubjectIdAndSourceAndScope(subjectId, source, scope)
                .orElseGet(ConsentRecord::new);
        record.setSubjectId(subjectId);
        record.setSource(source);
        record.setScope(scope);
        record.setStatus(ConsentStatus.REVOKED);
        record.setUpdatedAt(LocalDateTime.now());
        ConsentRecord saved = consentRecordRepository.save(record);
        securityAuditService.register("CONSENT_REVOKED", actor, "subjectId=" + subjectId + ",source=" + source + ",scope=" + scope);
        return saved;
    }

    public boolean hasConsent(String subjectId, FinanceSource source, String scope) {
        return consentRecordRepository.findBySubjectIdAndSourceAndScope(subjectId, source, scope)
                .map(record -> record.getStatus() == ConsentStatus.GRANTED)
                .orElse(false);
    }
}

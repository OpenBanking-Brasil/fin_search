package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.repository.SecurityAuditEntryRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.SecurityAuditEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditEntryRepository securityAuditEntryRepository;

    public void register(String eventType, String actor, String details) {
        SecurityAuditEntry entry = new SecurityAuditEntry();
        entry.setEventType(eventType);
        entry.setActor(actor);
        entry.setDetails(details);
        securityAuditEntryRepository.save(entry);
    }
}

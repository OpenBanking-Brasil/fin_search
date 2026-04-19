package br.aof.read_opendata_apis.financialassistant.application.repository;

import br.aof.read_opendata_apis.financialassistant.domain.model.SecurityAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityAuditEntryRepository extends JpaRepository<SecurityAuditEntry, Long> {
}

package br.aof.read_opendata_apis.financialassistant.application.repository;

import br.aof.read_opendata_apis.financialassistant.domain.model.ConsentRecord;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
    Optional<ConsentRecord> findBySubjectIdAndSourceAndScope(String subjectId, FinanceSource source, String scope);
}

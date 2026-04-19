package br.aof.read_opendata_apis.financialassistant.application.repository;

import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceCategory;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FinanceEventRepository extends JpaRepository<FinanceEvent, Long> {
    boolean existsByFingerprint(String fingerprint);
    List<FinanceEvent> findTop500ByOrderByOccurredAtDesc();
    List<FinanceEvent> findTop500ByCategoryOrderByOccurredAtDesc(FinanceCategory category);
    List<FinanceEvent> findByOccurredAtBetween(LocalDateTime from, LocalDateTime to);
}

package br.aof.read_opendata_apis.financialassistant.application.repository;

import br.aof.read_opendata_apis.financialassistant.domain.model.ApprovalStatus;
import br.aof.read_opendata_apis.financialassistant.domain.model.RecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {
    List<RecommendationItem> findTop100ByStatusOrderByCreatedAtDesc(ApprovalStatus status);
}

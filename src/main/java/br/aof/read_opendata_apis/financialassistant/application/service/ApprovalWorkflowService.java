package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.repository.RecommendationItemRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.ApprovalStatus;
import br.aof.read_opendata_apis.financialassistant.domain.model.RecommendationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final RecommendationItemRepository recommendationItemRepository;

    public List<RecommendationItem> listPendingApprovals() {
        return recommendationItemRepository.findTop100ByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING_APPROVAL);
    }

    public RecommendationItem decide(Long recommendationId, boolean approve, String note) {
        RecommendationItem item = recommendationItemRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));
        item.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        item.setDecisionNote(note);
        item.setDecidedAt(LocalDateTime.now());
        return recommendationItemRepository.save(item);
    }

    public RecommendationItem executeApproved(Long recommendationId, String note) {
        RecommendationItem item = recommendationItemRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));
        if (item.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Recommendation is not approved");
        }
        item.setStatus(ApprovalStatus.EXECUTED);
        item.setDecisionNote(note);
        item.setDecidedAt(LocalDateTime.now());
        return recommendationItemRepository.save(item);
    }
}

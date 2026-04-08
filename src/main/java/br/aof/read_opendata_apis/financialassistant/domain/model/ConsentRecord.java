package br.aof.read_opendata_apis.financialassistant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "finance_consent")
@Data
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinanceSource source;

    @Column(nullable = false, length = 120)
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentStatus status;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

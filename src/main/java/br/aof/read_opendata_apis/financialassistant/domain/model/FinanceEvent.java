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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_event")
@Data
public class FinanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinanceSource source;

    @Column(nullable = false, length = 180)
    private String sourceRef;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinanceEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinanceCategory category;

    @Column(length = 120)
    private String merchant;

    @Column(length = 120)
    private String counterparty;

    @Column(columnDefinition = "text")
    private String metadataJson;

    @Column(nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

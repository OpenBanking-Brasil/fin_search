package br.aof.read_opendata_apis.application.dto;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "opendata-new")
@Data
public class OpenDataDTO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String institution;

    @Column(nullable = false, length = 120)
    private String service;

    @Column(nullable = false, length = 120)
    private String familyType;

    @Column(nullable = false, length = 1200)
    private String url;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

}

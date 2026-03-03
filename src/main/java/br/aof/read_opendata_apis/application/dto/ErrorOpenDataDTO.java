package br.aof.read_opendata_apis.application.dto;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "opendata-error")
@Data
public class ErrorOpenDataDTO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String institution;

    @Column(nullable = false, length = 1200)
    private String url;

    @Column(nullable = false, length = 1200)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

}

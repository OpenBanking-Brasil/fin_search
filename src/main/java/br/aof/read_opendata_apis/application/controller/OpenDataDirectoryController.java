package br.aof.read_opendata_apis.application.controller;

import br.aof.read_opendata_apis.application.dto.InstituitionDTO;
import br.aof.read_opendata_apis.application.service.CentralDirectoryIntegration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/opendata", produces = MediaType.APPLICATION_JSON_VALUE)
public class OpenDataDirectoryController {

    private final CentralDirectoryIntegration centralDirectoryIntegration;

    public OpenDataDirectoryController(CentralDirectoryIntegration centralDirectoryIntegration) {
        this.centralDirectoryIntegration = centralDirectoryIntegration;
    }

    /**
     * Lista instituições e URLs de descoberta de Dados Abertos (opendata) conforme o Diretório Central.
     * Não requer token: o diretório e os catálogos opendata são públicos para leitura.
     */
    @GetMapping("/directory/institutions")
    public List<InstituitionDTO> listOpenDataInstitutions() {
        return centralDirectoryIntegration.findUrls();
    }
}

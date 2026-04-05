package br.aof.read_opendata_apis.application.service;

import br.aof.read_opendata_apis.application.dto.InstituitionDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReadBankDataService {
    private final ReadOpenDataEndpoints readOpenDataEndpoints;
    private final CentralDirectoryIntegration centralDirectoryIntegration;

    public void readFamilyTypeApis(List<String> famiTypes) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        famiTypes.forEach(familyType -> discoveryEndpoints(familyType));
    }

    public void discoveryEndpoints(String familyType) {
        log.info("Starting process for " + familyType);
        List<InstituitionDTO> endpoints = getEndpointsByServices(familyType, "personal");
        endpoints.forEach(endpoint -> sendData(readOpenDataEndpoints, endpoint.institution(), endpoint.url(), "personal", familyType));
        endpoints = getEndpointsByServices(familyType, "business");
        endpoints.forEach(endpoint -> sendData(readOpenDataEndpoints, endpoint.institution(), endpoint.url(), "business", familyType));
    }

    private List<InstituitionDTO> getEndpointsByServices(String service, String customer) {
        log.info("Read data from Central Directory");
        return centralDirectoryIntegration.findUrls().stream()
                .filter(endpoint -> endpoint.url().contains(service)
                        && endpoint.url().contains("opendata")
                        && !endpoint.url().contains("product-service")
                        && endpoint.url().contains(customer))
                .collect(Collectors.toList());
    }

    private List<InstituitionDTO> getEndpointsByChannels(String service) {
        log.info("Read data from Central Directory");
        return centralDirectoryIntegration.findUrls().stream()
                .filter(endpoint -> endpoint.url().contains(service) && !endpoint.url().contains("v1"))
                .distinct()
                .collect(Collectors.toList());
    }

    private void sendData(ReadOpenDataEndpoints readOpenDataEndpoints, String companyName, String endpoint, String customer, String familyType) {
        log.info("reading URL: " + endpoint+"\n to collect open data information from institution:"+companyName);
        try {
            readOpenDataEndpoints.sendServiceData(endpoint, companyName, customer, familyType);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            e.printStackTrace();
        }
    }
}

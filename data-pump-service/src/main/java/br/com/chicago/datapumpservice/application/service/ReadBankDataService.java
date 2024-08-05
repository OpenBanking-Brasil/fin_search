package br.com.chicago.datapumpservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
public class ReadBankDataService {

    public void readOpenDataAccounts() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting process for account system");
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByServices("accounts");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/accounts.json"));
    }

    private List<String> getEndpointsByServices(String service) {
        log.info("Read data from Central Directory");
        CentralDirectoryIntegration centralDirectoryIntegration = new CentralDirectoryIntegration();
        return centralDirectoryIntegration.findUrls().stream().filter(endpoint -> endpoint.contains(service) && !endpoint.contains("product-service") && endpoint.contains("personal")).distinct().collect(Collectors.toList());
    }

    private List<String> getEndpointsByAPIComum(String service) {
        log.info("Read data from Central Directory");
        CentralDirectoryIntegration centralDirectoryIntegration = new CentralDirectoryIntegration();
        return centralDirectoryIntegration.findUrls().stream().filter(endpoint -> endpoint.contains(service)).distinct().collect(Collectors.toList());
    }

    private List<String> getEndpointsByChannels(String service) {
        log.info("Read data from Central Directory");
        CentralDirectoryIntegration centralDirectoryIntegration = new CentralDirectoryIntegration();
        return centralDirectoryIntegration.findUrls().stream().filter(endpoint -> endpoint.contains(service) && !endpoint.contains("v1")).distinct().collect(Collectors.toList());
    }

    public void readOpenDataUnarranged() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting process for unarranged system");
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByServices("unarranged");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/unarranged.json"));
    }

    public void readOpenDataCreditCard() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting process for unarranged system");
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByServices("credit");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/credit.json"));
    }

    public void readOpenDataLoans() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting process for Loans system");
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByServices("loans");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/loans.json"));
    }

    public void readOpenDataInvoiceFinancing() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting process for Loans system");
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByServices("-invoice-financings");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/invoice-financings.json"));
    }

    public void readAPIComum() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting process for Loans system");
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByAPIComum("outages");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/comum.json"));
    }

    public void readOpenDataChannel() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        OpenDataAccountsIntegration openDataAccountsIntegration = new OpenDataAccountsIntegration();
        List<String> endpoints = getEndpointsByChannels("channels");
        endpoints.forEach(endpoint -> sendData(openDataAccountsIntegration, endpoint, "src/main/resources/channel.json"));
    }

    private void sendData(OpenDataAccountsIntegration openDataAccountsIntegration, String endpoint, String path) {
        log.info("Considering the following URL as API from Accounts: " + endpoint);
        try {
            openDataAccountsIntegration.sendServiceData(endpoint, path);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            e.printStackTrace();
        }
    }
}

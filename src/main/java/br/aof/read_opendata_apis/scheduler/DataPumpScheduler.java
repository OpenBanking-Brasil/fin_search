package br.aof.read_opendata_apis.scheduler;

import br.aof.read_opendata_apis.application.service.ReadBankDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Service
public class DataPumpScheduler {
    private final ReadBankDataService service;


    @Scheduled(fixedRate = 200000000)
    public void findAllActiveContractsJob() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting scheduled job to find all active contracts");

        List<String> familyTypes = new ArrayList<>();
        familyTypes.add("accounts");
        familyTypes.add("data-financings");
        familyTypes.add("unarranged");
        familyTypes.add("credit");
        familyTypes.add("loans");
        familyTypes.add("-invoice-financings");
        familyTypes.add("capitalization");
        familyTypes.add("investments");
        familyTypes.add("exchange");
        familyTypes.add("acquiring");
        familyTypes.add("pension");
        familyTypes.add("insurance");

        service.readFamilyTypeApis(familyTypes);

        log.info("Finishing scheduled job to find all active contracts");
    }
}

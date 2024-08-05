package br.com.chicago.datapumpservice.infrastructure.scheduler;

import br.com.chicago.datapumpservice.application.service.ReadBankDataService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@AllArgsConstructor
@Slf4j
@Service
public class DataPumpScheduler {
    private final ReadBankDataService service;


    @Scheduled(fixedRate = 200000000)
    public void findAllActiveContractsJob() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Starting scheduled job to find all active contracts");


        //service.readOpenDataAccounts();
        service.readOpenDataUnarranged();
        service.readOpenDataCreditCard();
        service.readOpenDataLoans();
        service.readOpenDataInvoiceFinancing();
        //service.readAPIComum();

        log.info("Finishing scheduled job to find all active contracts");
    }
}

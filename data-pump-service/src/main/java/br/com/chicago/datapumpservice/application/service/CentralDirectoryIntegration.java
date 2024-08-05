package br.com.chicago.datapumpservice.application.service;

import br.com.chicago.datapumpservice.application.dto.ParticipantDTO;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class CentralDirectoryIntegration {
    private RestTemplate restTemplate;

    private String baseUrl;

    public static ParticipantDTO[] getHttpConn(String url) {

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            org.apache.http.HttpResponse response = client.execute(httpGet);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(jsonResponse, ParticipantDTO[].class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CentralDirectoryIntegration() {
        this.restTemplate = new RestTemplate();
        this.baseUrl = "https://data.directory.openbankingbrasil.org.br/participants";
    }

    public List<String> findUrls() {
        List<String> bankUrls = new ArrayList<>();
        ParticipantDTO[] participantDTO = getHttpConn(this.baseUrl);
        Arrays.stream(participantDTO).forEach(participant -> {
            participant.getAuthorisationServers().forEach(authorisationServer ->{
                authorisationServer.getApiResources().forEach(apiResource -> {
                    apiResource.getApiDiscoveryEndpoints().forEach(apiDiscoveryEndpoint -> {
                        String endpoint = apiDiscoveryEndpoint.getApiEndpoint();
                        if(endpoint.contains("opendata"))
                            bankUrls.add(endpoint);
                    });
                });
            });
        });
        return bankUrls;
    }
}

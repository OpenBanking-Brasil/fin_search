package br.aof.read_opendata_apis.application.service;

import br.aof.read_opendata_apis.application.dto.InstituitionDTO;
import br.aof.read_opendata_apis.application.dto.ParticipantDTO;
import br.aof.read_opendata_apis.infrastructure.config.OpenFinanceProperties;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class CentralDirectoryIntegration {

    private final OpenFinanceProperties openFinanceProperties;

    public CentralDirectoryIntegration(OpenFinanceProperties openFinanceProperties) {
        this.openFinanceProperties = openFinanceProperties;
    }

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
            log.error("Falha ao obter participantes do diretório: {}", url, e);
        }
        return null;
    }

    private boolean matchesInstitutionFilter(String customerFriendlyName) {
        List<String> include = openFinanceProperties.getOpendata().getInstitutionInclude();
        if (include == null || include.isEmpty()) {
            return true;
        }
        if (customerFriendlyName == null) {
            return false;
        }
        String lower = customerFriendlyName.toLowerCase();
        return include.stream().anyMatch(s -> s != null && !s.isBlank() && lower.contains(s.toLowerCase().trim()));
    }

    public List<InstituitionDTO> findUrls() {
        List<InstituitionDTO> instituitions = new ArrayList<>();
        String baseUrl = openFinanceProperties.getDirectory().getParticipantsUrl();
        ParticipantDTO[] participantDTO = getHttpConn(baseUrl);
        if (participantDTO == null) {
            return instituitions;
        }
        Arrays.stream(participantDTO).forEach(participant -> {
            if (participant.getAuthorisationServers() == null) {
                return;
            }
            participant.getAuthorisationServers().forEach(authorisationServer -> {
                if (!matchesInstitutionFilter(authorisationServer.getCustomerFriendlyName())) {
                    return;
                }
                if (authorisationServer.getApiResources() == null) {
                    return;
                }
                authorisationServer.getApiResources().forEach(apiResource -> {
                    if (apiResource.getApiDiscoveryEndpoints() == null) {
                        return;
                    }
                    apiResource.getApiDiscoveryEndpoints().forEach(apiDiscoveryEndpoint -> {
                        String endpoint = apiDiscoveryEndpoint.getApiEndpoint();
                        if (endpoint != null && endpoint.contains("opendata")) {
                            instituitions.add(new InstituitionDTO(
                                    authorisationServer.getCustomerFriendlyName(),
                                    endpoint));
                        }
                    });
                });
            });
        });
        return instituitions;
    }
}

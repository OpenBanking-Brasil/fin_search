package br.aof.read_opendata_apis.application.service;


import br.aof.read_opendata_apis.application.dto.OpenDataDTO;
import br.aof.read_opendata_apis.application.dto.ErrorOpenDataDTO;
import br.aof.read_opendata_apis.application.repository.ErrorOpendataRepository;
import br.aof.read_opendata_apis.application.repository.OpendataRepository;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReadOpenDataEndpoints {
    private final OpendataRepository repository;
    private final ErrorOpendataRepository errorRepository;

    public static Object getHttpConn(String url) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (certificate, authType) -> true).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                sslContext, NoopHostnameVerifier.INSTANCE);

        try (CloseableHttpClient client = HttpClients.custom().setSSLSocketFactory(sslsf).build()) {
            HttpGet httpGet = new HttpGet(url);
            HttpResponse response = client.execute(httpGet);
            String jsonResponse = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(jsonResponse, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void sendServiceData(String baseUrl, String companyName, String service, String familyType) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        Object accounts = getHttpConn(baseUrl);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode accountData = new ObjectMapper().readTree(mapper.writeValueAsString(accounts));

        saveJsonFile(accountData, companyName, service, familyType, baseUrl);

        if(hasNextPage(accounts, baseUrl, companyName))
        {
            try {
                JsonNode linkData = accountData.get("links");
                String nextLink = String.valueOf(linkData.get("next"));
                nextLink = nextLink.substring(1, nextLink.length() - 1);
                if(!nextLink.equalsIgnoreCase(baseUrl))
                    sendServiceData(nextLink, companyName, service, familyType);
                else
                    saveErrorData(companyName, baseUrl, "Link of next page is equal than actual page" );
            }catch (Exception ex) {
                saveErrorData(companyName, baseUrl, "Payload without next page information" );
                log.info("Payload without next page information");
            }
        }

    }

    private void saveJsonFile(JsonNode accountData, String companyName, String service, String familyType, String baseUrl) throws IOException {
        saveData(companyName, familyType, service, baseUrl, accountData.toString());
    }

    private void saveData(String institution, String familyType, String service, String url, String payload){
        var entity = new OpenDataDTO();
        entity.setInstitution(institution);
        entity.setUrl(url);
        entity.setPayload(payload);
        entity.setService(service);
        entity.setFamilyType(familyType);
        repository.save(entity);
    }

    private void saveErrorData(String institution, String url, String reason){
        var errorEntity = new ErrorOpenDataDTO();
        errorEntity.setInstitution(institution);
        errorEntity.setUrl(url);
        errorEntity.setReason(reason);
        errorRepository.save(errorEntity);
    }

    private boolean hasNextPage(Object accounts, String baseUrl, String companyName) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode accountData = new ObjectMapper().readTree(mapper.writeValueAsString(accounts));
        try {
            JsonNode metaData = accountData.get("meta");
            Integer totalPages = Integer.valueOf(metaData.get("totalPages").toString());
            String nextLink;

            if (totalPages > 1) {
                JsonNode linkData = accountData.get("links");
                try {
                    nextLink = linkData.get("next").toString();
                } catch (Exception ex) {
                    return false;
                }
                if (!nextLink.equals(baseUrl))
                    return true;
            }
        } catch (Exception ex){
            saveErrorData(companyName, baseUrl, "Payload without next page information" );
            log.info("Payload sem o bloco de paginação");
        }
        return false;
    }
}

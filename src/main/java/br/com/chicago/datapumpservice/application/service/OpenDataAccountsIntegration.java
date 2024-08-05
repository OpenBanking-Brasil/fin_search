package br.com.chicago.datapumpservice.application.service;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class OpenDataAccountsIntegration {
    boolean isFirstElement;

    public OpenDataAccountsIntegration() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        isFirstElement = true;
    }
    public static Object getHttpConn(String url) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(null, (certificate, authType) -> true).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                sslContext, NoopHostnameVerifier.INSTANCE);

        try (CloseableHttpClient client = HttpClients.custom().setSSLSocketFactory(sslsf).build()) {
            HttpGet httpGet = new HttpGet(url);
            org.apache.http.HttpResponse response = client.execute(httpGet);
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

    public void sendServiceData(String baseUrl, String path) throws IOException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        Object accounts = getHttpConn(baseUrl);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode accountData = new ObjectMapper().readTree(mapper.writeValueAsString(accounts));

        saveJsonFile(accountData, path);

        if(hasNextPage(accounts, baseUrl))
        {
            try {
                JsonNode linkData = accountData.get("links");
                String nextLink = String.valueOf(linkData.get("next"));
                nextLink = nextLink.substring(1, nextLink.length() - 1);
                sendServiceData(nextLink, path);
            }catch (Exception ex) {
                log.info("Payload without next page information");
            }
        }

    }

    private void saveJsonFile(JsonNode accountData, String path) throws IOException {
        String data;
        if(isFirstElement)
            data = "[" + accountData.toString() + ", ";
        else
            data = accountData.toString() + ", ";
        isFirstElement = false;
        Files.write(
                Paths.get(path),
                data.getBytes(),
                StandardOpenOption.APPEND);
    }

    private boolean hasNextPage(Object accounts, String baseUrl) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode accountData = new ObjectMapper().readTree(mapper.writeValueAsString(accounts));
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
            if(nextLink != baseUrl)
                return true;
        }
        return false;
    }
}

package br.aof.read_opendata_apis.financialassistant.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "financial-assistant.security")
public class FinancialAssistantSecurityProperties {

    /**
     * 32-byte key in plain text for local development only.
     */
    private String encryptionKey = "change-this-key-32-bytes-minimum!";

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}

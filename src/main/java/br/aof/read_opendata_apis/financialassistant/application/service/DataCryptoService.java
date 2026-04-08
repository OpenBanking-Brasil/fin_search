package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.infrastructure.config.FinancialAssistantSecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DataCryptoService {

    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_SIZE = 12;
    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public DataCryptoService(FinancialAssistantSecurityProperties properties) {
        byte[] keyBytes = normalizeKey(properties.getEncryptionKey());
        this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt payload", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            byte[] iv = new byte[IV_SIZE];
            byte[] ciphertext = new byte[payload.length - IV_SIZE];
            System.arraycopy(payload, 0, iv, 0, IV_SIZE);
            System.arraycopy(payload, IV_SIZE, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt payload", e);
        }
    }

    private byte[] normalizeKey(String key) {
        byte[] raw = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        byte[] normalized = new byte[32];
        int length = Math.min(raw.length, normalized.length);
        System.arraycopy(raw, 0, normalized, 0, length);
        return normalized;
    }
}

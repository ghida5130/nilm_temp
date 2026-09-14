package com.nilm.monitoring.subject.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Base64;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PiiCodec {
    private final byte[] searchKey;
    private final byte[] encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public PiiCodec(@Value("${app.pii.search-key:local-development-only}") String searchKey,
                    @Value("${app.pii.encryption-key:}") String encryptionKey,
                    @Value("${app.pii.require-encryption:${app.security.enabled:false}}") boolean requireEncryption) {
        this.searchKey = searchKey.getBytes(StandardCharsets.UTF_8);
        this.encryptionKey = encryptionKey.isBlank() ? null : Base64.getDecoder().decode(encryptionKey);
        if (this.encryptionKey != null && this.encryptionKey.length != 32) {
            throw new IllegalArgumentException("app.pii.encryption-key must be a base64 encoded 256-bit key");
        }
        if (requireEncryption && this.encryptionKey == null) {
            throw new IllegalStateException("운영 환경에는 app.pii.encryption-key가 필요합니다.");
        }
        if (requireEncryption && "local-development-only".equals(searchKey)) {
            throw new IllegalStateException("운영 환경에는 별도의 app.pii.search-key가 필요합니다.");
        }
    }

    public String decrypt(byte[] value) {
        if (value == null) return null;
        if (encryptionKey == null) return new String(value, StandardCharsets.UTF_8);
        if (value.length < 29 || value[0] != 1) throw new IllegalArgumentException("지원하지 않는 개인정보 암호문입니다.");
        try {
            byte[] nonce = Arrays.copyOfRange(value, 1, 13);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(Arrays.copyOfRange(value, 13, value.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("개인정보를 복호화할 수 없습니다.", e);
        }
    }

    public byte[] encrypt(String value) {
        if (encryptionKey == null) return value.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[13 + encrypted.length];
            result[0] = 1;
            System.arraycopy(nonce, 0, result, 1, nonce.length);
            System.arraycopy(encrypted, 0, result, 13, encrypted.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("개인정보를 암호화할 수 없습니다.", e);
        }
    }

    public String blindIndex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(searchKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalize(value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이름 검색 인덱스를 생성할 수 없습니다.", e);
        }
    }

    public String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", "").trim();
    }
}

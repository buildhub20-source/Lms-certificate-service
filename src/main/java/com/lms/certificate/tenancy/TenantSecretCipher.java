package com.lms.certificate.tenancy;

import com.lms.certificate.config.TenantRuntimeConfig;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/** Decrypts tenant credentials stored in the LMS platform control plane. */
@Service
public class TenantSecretCipher {
    private final TenantRuntimeConfig config;

    public TenantSecretCipher(TenantRuntimeConfig config) {
        this.config = config;
    }

    public String decrypt(String encryptedValue) {
        try {
            byte[] packed = Base64.getDecoder().decode(encryptedValue);
            if (packed.length <= 12) throw new IllegalArgumentException("Invalid encrypted tenant secret");
            byte[] nonce = Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read tenant database credentials", ex);
        }
    }

    private SecretKey key() {
        try {
            String encoded = config.getCredentialEncryptionKey();
            if (encoded == null || encoded.isBlank()) {
                throw new IllegalArgumentException("TENANT_CREDENTIAL_ENCRYPTION_KEY is required");
            }
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != 32) throw new IllegalArgumentException("Expected 32 bytes");
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid tenant credential encryption key", ex);
        }
    }
}

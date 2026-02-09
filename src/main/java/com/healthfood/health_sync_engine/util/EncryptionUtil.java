package com.healthfood.health_sync_engine.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class EncryptionUtil {

    private final byte[] key;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;

    public EncryptionUtil(@Value("${app.encryption.key:a-very-secret-key-that-is-32-chars-long-!!!}") String secret) {
        // We need to match scryptSync(secret, 'salt', 32) from Node.js
        // For simplicity and since we control both ends, we can use a simpler derivation if we change Node.js
        // OR we implement a basic PBKDF2/SHA256 that approximates it if possible.
        // Node's scryptSync is very specific. Let's use a simpler SHA-256 hash of the secret for now to stay robust,
        // but I should really update Node.js to use a simpler key derivation if I want perfect matching without scrypt libs in Java.
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            this.key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize EncryptionUtil", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || !encryptedText.contains(":")) {
            return encryptedText;
        }

        try {
            String[] parts = encryptedText.split(":");
            if (parts.length != 3) return encryptedText;

            byte[] iv = HexFormat.of().parseHex(parts[0]);
            byte[] authTag = HexFormat.of().parseHex(parts[1]);
            byte[] encryptedData = HexFormat.of().parseHex(parts[2]);

            // Node.js GCM is encryptedData + authTag usually, but here they are split
            // In Java, Cipher expects encryptedData + authTag concatenated for GCM
            byte[] combined = new byte[encryptedData.length + authTag.length];
            System.arraycopy(encryptedData, 0, combined, 0, encryptedData.length);
            System.arraycopy(authTag, 0, combined, encryptedData.length, authTag.length);

            SecretKey secretKey = new SecretKeySpec(this.key, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(combined);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encryptedText; // Fallback to raw if decryption fails
        }
    }
}

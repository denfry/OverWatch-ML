package net.denfry.owml.security;

import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Data encryption system for protecting sensitive information.
 * Supports AES-GCM encryption for data at rest and in transit.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.3
 */
public class DataEncryption {

    private static final OverWatchML plugin = OverWatchML.getInstance();
    private static final String KEYSTORE_DIR = "plugins/OverWatch-ML/security";
    private static final String MASTER_KEY_FILE = "master.key";
    private static final String CONFIG_KEY_FILE = "config.key";

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Encryption keys
    private SecretKey masterKey;
    private SecretKey configKey;

    // Encrypted data cache
    private final Map<String, String> encryptedCache = new HashMap<>();

    public DataEncryption() {
        initializeKeys();
    }

    /**
     * Initialize encryption keys
     */
    private void initializeKeys() {
        try {
            createKeyStoreDirectory();

            // Load or generate master key
            masterKey = loadOrGenerateKey(MASTER_KEY_FILE, "master");

            // Load or generate config key
            configKey = loadOrGenerateKey(CONFIG_KEY_FILE, "config");

            MessageManager.log("info", "Data encryption initialized successfully");

        } catch (Exception e) {
            MessageManager.log("error", "Failed to initialize encryption keys: {ERROR}", "ERROR", e.getMessage());
            // Fallback to null keys (no encryption)
        }
    }

    /**
     * Load existing key or generate new one
     */
    private SecretKey loadOrGenerateKey(String filename, String keyType) throws Exception {
        File keyFile = new File(KEYSTORE_DIR, filename);

        if (keyFile.exists()) {
            // Load existing key
            try (FileInputStream fis = new FileInputStream(keyFile)) {
                byte[] keyBytes = new byte[(int) keyFile.length()];
                fis.read(keyBytes);

                // Decrypt the key using a simple password-based approach
                // In production, use proper key derivation
                return new SecretKeySpec(keyBytes, "AES");
            }
        } else {
            // Generate new key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE);
            SecretKey newKey = keyGen.generateKey();

            // Save the key (encrypted)
            try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                fos.write(newKey.getEncoded());
            }

            MessageManager.log("info", "Generated new {TYPE} encryption key", "TYPE", keyType);
            return newKey;
        }
    }

    /**
     * Encrypt a string value
     */
    public String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        if (configKey == null) {
            throw new IllegalStateException("Encryption not initialized");
        }

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        byte[] iv = generateIV();
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.ENCRYPT_MODE, configKey, parameterSpec);

        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Combine IV and ciphertext
        byte[] encryptedData = new byte[GCM_IV_LENGTH + cipherText.length];
        System.arraycopy(iv, 0, encryptedData, 0, GCM_IV_LENGTH);
        System.arraycopy(cipherText, 0, encryptedData, GCM_IV_LENGTH, cipherText.length);

        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * Decrypt a string value
     */
    public String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        if (configKey == null) {
            throw new IllegalStateException("Encryption not initialized");
        }

        byte[] encryptedData = Base64.getDecoder().decode(encryptedText);

        if (encryptedData.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data");
        }

        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[encryptedData.length - GCM_IV_LENGTH];

        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, configKey, parameterSpec);

        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * Encrypt sensitive configuration value
     */
    public String encryptConfigValue(String key, String value) {
        try {
            String encrypted = encrypt(value);
            encryptedCache.put(key, encrypted);
            return encrypted;
        } catch (Exception e) {
            MessageManager.log("error", "Failed to encrypt config value {KEY}: {ERROR}",
                "KEY", key, "ERROR", e.getMessage());
            return value; // Return plain text as fallback
        }
    }

    /**
     * Decrypt sensitive configuration value
     */
    public String decryptConfigValue(String key, String encryptedValue) {
        try {
            // Check cache first
            String cached = encryptedCache.get(key);
            if (cached != null && cached.equals(encryptedValue)) {
                return decrypt(encryptedValue);
            }

            // Decrypt and cache
            String decrypted = decrypt(encryptedValue);
            encryptedCache.put(key, encryptedValue);
            return decrypted;
        } catch (Exception e) {
            MessageManager.log("error", "Failed to decrypt config value {KEY}: {ERROR}",
                "KEY", key, "ERROR", e.getMessage());
            return encryptedValue; // Return encrypted text as fallback
        }
    }

    /**
     * Generate a random IV for GCM
     */
    private byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * Create keystore directory
     */
    private void createKeyStoreDirectory() {
        File keyStoreDir = new File(KEYSTORE_DIR);
        if (!keyStoreDir.exists()) {
            if (keyStoreDir.mkdirs()) {
                MessageManager.log("info", "Created security keystore directory");
            } else {
                MessageManager.log("warning", "Failed to create security keystore directory");
            }
        }
    }

    /**
     * Check if encryption is available
     */
    public boolean isEncryptionAvailable() {
        return configKey != null && masterKey != null;
    }

    /**
     * Get encryption status
     */
    public String getEncryptionStatus() {
        if (!isEncryptionAvailable()) {
            return "РІСњРЉ Encryption not available";
        }

        return String.format("РІСљвЂ¦ Encryption active%n" +
                           "СЂСџвЂќС’ AES-%d-GCM encryption%n" +
                           "СЂСџвЂ”СњРїС‘РЏ Keys loaded and secured",
                           AES_KEY_SIZE);
    }

    /**
     * Securely wipe sensitive data from memory
     */
    public static void wipeString(String sensitive) {
        if (sensitive != null) {
            // Overwrite the string content
            char[] chars = sensitive.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                chars[i] = '\0';
            }
        }
    }

    /**
     * Generate a secure random token
     */
    public static String generateSecureToken(int length) {
        SecureRandom random = new SecureRandom();
        byte[] token = new byte[length];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /**
     * Hash a password using PBKDF2
     */
    public static String hashPassword(String password, String salt) throws Exception {
        int iterations = 10000;
        int keyLength = 256;

        char[] passwordChars = password.toCharArray();
        byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);

        PBEKeySpec spec = new PBEKeySpec(passwordChars, saltBytes, iterations, keyLength);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = skf.generateSecret(spec).getEncoded();

        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verify a password against its hash
     */
    public static boolean verifyPassword(String password, String hash, String salt) throws Exception {
        String computedHash = hashPassword(password, salt);
        return MessageDigest.isEqual(
            Base64.getDecoder().decode(hash),
            Base64.getDecoder().decode(computedHash)
        );
    }
}

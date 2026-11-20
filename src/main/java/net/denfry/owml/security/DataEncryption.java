package net.denfry.owml.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

public class DataEncryption {
   private static final OverWatchML plugin = OverWatchML.getInstance();
   private static final String KEYSTORE_DIR = "plugins/OverWatch-ML/security";
   private static final String MASTER_KEY_FILE = "master.key";
   private static final String CONFIG_KEY_FILE = "config.key";
   private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
   private static final int AES_KEY_SIZE = 256;
   private static final int GCM_IV_LENGTH = 12;
   private static final int GCM_TAG_LENGTH = 128;
   private SecretKey masterKey;
   private SecretKey configKey;
   private final Map<String, String> encryptedCache = new ConcurrentHashMap<>();

   public DataEncryption() {
      this.initializeKeys();
   }

   private void initializeKeys() {
      try {
         this.createKeyStoreDirectory();
         this.masterKey = this.loadOrGenerateKey("master.key", "master");
         this.configKey = this.loadOrGenerateKey("config.key", "config");
         MessageManager.log("info", "Data encryption initialized successfully");
      } catch (Exception var2) {
         MessageManager.log("error", "Failed to initialize encryption keys: {ERROR}", "ERROR", var2.getMessage());
      }
   }

   private SecretKey loadOrGenerateKey(String filename, String keyType) throws Exception {
      File keyFile = new File("plugins/OverWatch-ML/security", filename);
      if (keyFile.exists()) {
         SecretKeySpec var15;
         try (FileInputStream fis = new FileInputStream(keyFile)) {
            byte[] keyBytes = new byte[(int)keyFile.length()];
            fis.read(keyBytes);
            var15 = new SecretKeySpec(keyBytes, "AES");
         }

         return var15;
      } else {
         KeyGenerator keyGen = KeyGenerator.getInstance("AES");
         keyGen.init(256);
         SecretKey newKey = keyGen.generateKey();

         try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            fos.write(newKey.getEncoded());
         }

         MessageManager.log("info", "Generated new {TYPE} encryption key", "TYPE", keyType);
         return newKey;
      }
   }

   public String encrypt(String plainText) throws Exception {
      if (plainText == null || plainText.isEmpty()) {
         return plainText;
      } else if (this.configKey == null) {
         throw new IllegalStateException("Encryption not initialized");
      } else {
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         byte[] iv = this.generateIV();
         GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
         cipher.init(1, this.configKey, parameterSpec);
         byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
         byte[] encryptedData = new byte[12 + cipherText.length];
         System.arraycopy(iv, 0, encryptedData, 0, 12);
         System.arraycopy(cipherText, 0, encryptedData, 12, cipherText.length);
         return Base64.getEncoder().encodeToString(encryptedData);
      }
   }

   public String decrypt(String encryptedText) throws Exception {
      if (encryptedText == null || encryptedText.isEmpty()) {
         return encryptedText;
      } else if (this.configKey == null) {
         throw new IllegalStateException("Encryption not initialized");
      } else {
         byte[] encryptedData = Base64.getDecoder().decode(encryptedText);
         if (encryptedData.length < 12) {
            throw new IllegalArgumentException("Invalid encrypted data");
         } else {
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[encryptedData.length - 12];
            System.arraycopy(encryptedData, 0, iv, 0, 12);
            System.arraycopy(encryptedData, 12, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(2, this.configKey, parameterSpec);
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
         }
      }
   }

   public String encryptConfigValue(String key, String value) {
      try {
         String encrypted = this.encrypt(value);
         this.encryptedCache.put(key, encrypted);
         return encrypted;
      } catch (Exception var4) {
         MessageManager.log("error", "Failed to encrypt config value {KEY}: {ERROR}", "KEY", key, "ERROR", var4.getMessage());
         return value;
      }
   }

   public String decryptConfigValue(String key, String encryptedValue) {
      try {
         String cached = this.encryptedCache.get(key);
         if (cached != null && cached.equals(encryptedValue)) {
            return this.decrypt(encryptedValue);
         } else {
            String decrypted = this.decrypt(encryptedValue);
            this.encryptedCache.put(key, encryptedValue);
            return decrypted;
         }
      } catch (Exception var5) {
         MessageManager.log("error", "Failed to decrypt config value {KEY}: {ERROR}", "KEY", key, "ERROR", var5.getMessage());
         return encryptedValue;
      }
   }

   private byte[] generateIV() {
      byte[] iv = new byte[12];
      SecureRandom random = new SecureRandom();
      random.nextBytes(iv);
      return iv;
   }

   private void createKeyStoreDirectory() {
      File keyStoreDir = new File("plugins/OverWatch-ML/security");
      if (!keyStoreDir.exists()) {
         if (keyStoreDir.mkdirs()) {
            MessageManager.log("info", "Created security keystore directory");
         } else {
            MessageManager.log("warning", "Failed to create security keystore directory");
         }
      }
   }

   public boolean isEncryptionAvailable() {
      return this.configKey != null && this.masterKey != null;
   }

   public String getEncryptionStatus() {
      return !this.isEncryptionAvailable()
         ? "❌ Encryption not available"
         : String.format("✅ Encryption active%n\ud83d\udd10 AES-%d-GCM encryption%n\ud83d\udddd️ Keys loaded and secured", 256);
   }

   public static void wipeString(String sensitive) {
      if (sensitive != null) {
         char[] chars = sensitive.toCharArray();

         for (int i = 0; i < chars.length; i++) {
            chars[i] = 0;
         }
      }
   }

   public static String generateSecureToken(int length) {
      SecureRandom random = new SecureRandom();
      byte[] token = new byte[length];
      random.nextBytes(token);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
   }

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

   public static boolean verifyPassword(String password, String hash, String salt) throws Exception {
      String computedHash = hashPassword(password, salt);
      return MessageDigest.isEqual(Base64.getDecoder().decode(hash), Base64.getDecoder().decode(computedHash));
   }
}

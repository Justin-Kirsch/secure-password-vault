package com.example.password_generator;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

// Utility class for encrypting / decrypting data with AES-GCM using a master password
public class CryptoUtils {

    // Algorithm + parameters used for AES-GCM and key derivation
    private static final String ENCRYPT_ALGO = "AES/GCM/NoPadding"; // AES encryption algorithm with GCM mode and no padding
    private static final int TAG_LENGTH_BIT = 128; // Length of the Auth Tags
    private static final int IV_LENGTH_BYTE = 12;  // Standard for GCM
    private static final int SALT_LENGTH_BYTE = 16; // Length of the salt used for key derivation
    private static final int AES_KEY_BIT = 256; // Key size for AES encryption
    private static final int ITERATION_COUNT = 65536; // High number against brute force

    // 1. Encrypt
    public static String encrypt(String passwordToStore, String masterPassword) throws Exception {
        // 1. Generating random Salt
        byte[] salt = getRandomNonce(SALT_LENGTH_BYTE);

        // 2. Generating randomized initialization vector
        byte[] iv = getRandomNonce(IV_LENGTH_BYTE);

        // 3. Deriving Secret key from Master password + Salt
        SecretKey secretKey = getSecretKey(masterPassword, salt);

        // 4. Preparing encryption
        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

        // 5. Actual encryption
        byte[] cipherText = cipher.doFinal(passwordToStore.getBytes(StandardCharsets.UTF_8));

        // 6. Pack everything together (salt + IV + ciphertext) so that it can be decrypted later.
        // Using ByteBuffer, to easily concatenate byte arrays
        byte[] cipherTextWithIvSalt = ByteBuffer.allocate(iv.length + salt.length + cipherText.length)
                .put(iv)
                .put(salt)
                .put(cipherText)
                .array();

        // Returning as Base64 String, so it can be saved as a file more easily
        return Base64.getEncoder().encodeToString(cipherTextWithIvSalt);
    }

    // 2. Decrypt
    public static String decrypt(String encodedString, String masterPassword) throws Exception {
        // decoding Base64
        byte[] decode = Base64.getDecoder().decode(encodedString);
        ByteBuffer bb = ByteBuffer.wrap(decode);

        // Extract values (the order must be exactly the same as for Encrypt!)
        byte[] iv = new byte[IV_LENGTH_BYTE];
        bb.get(iv);

        byte[] salt = new byte[SALT_LENGTH_BYTE];
        bb.get(salt);

        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);

        // Restore key (same master password + extracted salt = same key)
        SecretKey secretKey = getSecretKey(masterPassword, salt);

        Cipher cipher = Cipher.getInstance(ENCRYPT_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

        byte[] plainText = cipher.doFinal(cipherText);

        return new String(plainText, StandardCharsets.UTF_8);
    }

    // Support method: key derivation (PBKDF2)
    private static SecretKey getSecretKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        // Wandelt das Master-Passwort in einen 256-Bit AES Key um
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, AES_KEY_BIT);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    // Support method: Random generator
    private static byte[] getRandomNonce(int length) {
        byte[] nonce = new byte[length];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }
}
package com.example.password_generator;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

// Manages the master password: hashing, verification and storage on disk
public class AuthManager {

    // File where salt and password hash are stored (per user under APPDATA)
    private static final Path AUTH_PATH = Path.of(
            System.getenv("APPDATA"),
            "PasswordGenerator",
            "master.config"
    );

    // Parameters for PBKDF2 hashing
    private static final int SALT_LEN = 16; // Length of the salt in bytes
    private static final int ITERATIONS = 65536; // Number of iterations for hashing
    private static final int KEY_LEN = 256; // Length of the generated key in bits

    // Checks whether the file exists and is not empty
    public static boolean isMasterPasswordSet() {
        File f = AUTH_PATH.toFile();
        return f.exists() && f.length() > 0;
    }

    // Saves a NEW master password (on first start)
    public void setMasterPassword(String password) throws Exception {
        // 1. generating Salt
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);

        // 2. Hashing password
        byte[] hash = hashPassword(password, salt);

        // 3. Writing salt and hash to file (format: salt:hash)
        String saltStr = Base64.getEncoder().encodeToString(salt);
        String hashStr = Base64.getEncoder().encodeToString(hash);

        Files.createDirectories(AUTH_PATH.getParent());
        Files.writeString(AUTH_PATH, saltStr + ":" + hashStr);
    }

    // Verifies the input during login
    public boolean verifyMasterPassword(String inputPassword) throws Exception {
        if (!isMasterPasswordSet()) return false;

        // 1. Read stored string
        String content = Files.readString(AUTH_PATH);

        String[] parts = content.split(":");

        if (parts.length != 2) return false; // Datei beschädigt

        byte[] storedSalt = Base64.getDecoder().decode(parts[0]);
        byte[] storedHash = Base64.getDecoder().decode(parts[1]);

        // 2. Hash input with the same salt
        byte[] inputHash = hashPassword(inputPassword, storedSalt);

        // 3. Comparing
        return Arrays.equals(storedHash, inputHash);
    }

    // Support method: the actual hashing
    private byte[] hashPassword(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }
}
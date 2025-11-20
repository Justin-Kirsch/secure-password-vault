# Password Generator & Password Manager

A JavaFX application that combines a secure password generator with an encrypted password manager.  
All stored entries are encrypted on disk using a master password. The master password itself is **never** stored in plaintext.

## Screenshots

- **Password generator**

<img width="486" height="433" alt="grafik" src="https://github.com/user-attachments/assets/f0e04081-4c71-4876-bc17-13a6962a4076" />


- **Password manager**

<img width="492" height="462" alt="grafik" src="https://github.com/user-attachments/assets/04462aea-451c-4d58-a142-dddd96db63ba" />


## Features

- **Password generator**
  - Selectable character groups: uppercase, lowercase, digits, symbols.
  - Slider to control password length.
  - Ensures at least one character from every selected group.
  - Copies generated passwords to the clipboard.

- **Password manager**
  - Stores entries with: service / website, username, password.
  - Data is encrypted using AES‑256‑GCM and a key derived from the master password.
  - TableView with masked password column by default (shows `******`).
  - Option to reveal passwords in the table via a Filter menu.
  - Inline password field with its own **Show password** checkbox.
  - CRUD operations: add, edit, delete entries.
  - Copy password of the selected entry to the clipboard.

- **Master password handling**
  - First run: asks you to set a master password.
  - Later runs: login dialog to enter the existing master password.
  - Optional "Remember me for 5 minutes" login session.
  - Ability to change the master password later from the Password Manager.

- **Security**
  - Master password is stored as a salted PBKDF2 hash (no plaintext storage).
  - Password data is encrypted at rest with AES‑GCM.
  - Per‑encryption random salt and IV.

---

## Project structure (main classes)

- `Main` – JavaFX application entry point. Loads the main FXML and shows the generator window.
- `MainController` – Controller for the password generator UI and for opening the Password Manager.
- `PasswordManagerController` – Controller for the password manager window and table.
- `PasswordEntry` – Simple model class representing a single table row.
- `AuthManager` – Manages master password hashing, verification and storage.
- `CryptoUtils` – Handles AES‑GCM encryption/decryption and key derivation.

FXML layouts:

- `password_generator_ui.fxml` – Main generator window.
- `password_manager_ui.fxml` – Password manager window.

---

## Main flow

### 1. Start application

1. `Main` launches JavaFX and loads `password_generator_ui.fxml`.
2. `MainController` is created and wired to the UI.
3. You can immediately generate passwords using the generator.

### 2. Opening the Password Manager

From the main window there are two important actions that open the manager:

- `onOpenManagerClick()` – Open manager without transferring a generated password.
- `onCreateNewUserClick()` – Open manager and transfer the currently generated password as a default entry password.

Both actions call `initiateLoginFlow(passwordToTransfer)` inside `MainController`.

### 3. Login / first‑time setup

`MainController.initiateLoginFlow` decides what to do:

1. **Check if a master password is already configured** using `AuthManager.isMasterPasswordSet()`.
2. If a master password exists and a cached master password is still valid (within 5 minutes), the manager window is opened directly without showing a dialog.
3. If a master password exists but the cache has expired, a **Login** dialog is shown.
4. If no master password exists yet, a **Setup Master Password** dialog is shown instead.

#### Login dialog

- Shows:
  - `PasswordField` for entering the master password.
  - "Show password" checkbox to toggle between masked and visible text.
  - "Remember me for 5 minutes" checkbox.
  - Login button.
- On login:
  - `AuthManager.verifyMasterPassword(enteredPassword)` validates the input.
  - If valid and "Remember me" is checked, the master password and timestamp are cached in memory for 5 minutes.
  - On success the dialog is closed and `openManagerWindow(passwordToTransfer, enteredPassword)` is called.

#### Setup dialog (first run)

- Asks for:
  - New master password.
  - Confirmation of the master password.
  - Shared "Show password" checkbox that can reveal both fields.
- On save:
  - Validates non‑empty and matching passwords.
  - Calls `authManager.setMasterPassword(newPassword)` to persist the hash.
  - Caches the master password in memory for the current session.
  - Opens the manager window with `openManagerWindow(passwordToTransfer, newPassword)`.

### 4. Password Manager window

When `MainController.openManagerWindow(passwordToTransfer, masterPassword)` is called:

1. `password_manager_ui.fxml` is loaded and a new `Stage` is created.
2. The `PasswordManagerController` is obtained.
3. `setMasterPassword(masterPassword)` is called so the manager can decrypt existing entries.
4. If `passwordToTransfer` is not empty, `setGeneratedPassword(passwordToTransfer)` pre‑fills the entry password field.
5. The manager window is positioned next to the main window and shown.

Inside `PasswordManagerController`:

- `initialize()` wires the table columns to the `PasswordEntry` properties.
- The `TableView` selection listener populates the edit fields when you select a row.
- Buttons call handlers for add/edit/delete/copy.

---

## Password Manager UI details

### Table and masking behavior

- The `TableView` has three columns:
  - `Service / Website`
  - `Username`
  - `Password`
- The password column (`colPassword`) is **masked by default**:
  - A custom cell factory is set in `initialize()`.
  - For each non‑empty cell, the displayed text is either `******` or the real password.

This is controlled by:

- `@FXML private CheckMenuItem showPasswordsInTableMenuItem;`
- `private boolean showPasswordsInTable = false;`

And the handler:

```java
@FXML
protected void onToggleShowPasswordsInTable() {
    if (showPasswordsInTableMenuItem != null) {
        showPasswordsInTable = showPasswordsInTableMenuItem.isSelected();
        passwordTable.refresh();
    }
}
```

So:

- When the **Filter → Show passwords in table** menu item is unchecked, every cell in the password column renders `******`.
- When it is checked, the real password text is shown.

### Inline password field

- Below the table is an input area with fields for service, username and password.
- The password input has:
  - A `PasswordField` and a hidden `TextField`.
  - A `Show password` checkbox that toggles between them.
- This only affects the **edit field**; the table masking is controlled by the Filter menu as described above.

### CRUD operations

- **Add Entry**: validates non‑empty fields, adds a new `PasswordEntry` to the observable list and calls `saveEntries()`.
- **Edit Entry**: updates the selected row with the values from the input fields and calls `saveEntries()`.
- **Delete Entry**: removes the selected row and calls `saveEntries()`.
- **Copy Password**: copies the selected row’s password to the system clipboard.

All changes are persisted encrypted to disk (see below).

---

## AuthManager – Master password hashing and verification

`AuthManager` is responsible for **storing and verifying the master password** without ever writing it to disk in plaintext.

### Storage location

The master password hash is stored under the user’s roaming profile, in:

```text
%APPDATA%\\PasswordGenerator\\master.config
```

This file contains **two values** separated by a colon `:`:

```text
base64(salt):base64(hash)
```

- `salt` – a random 16‑byte salt.
- `hash` – the PBKDF2‑derived hash of the master password.

### Hashing algorithm

`AuthManager` uses PBKDF2 with HMAC‑SHA‑256:

- Salt length: `16` bytes.
- Iteration count: `65536` (to slow down brute‑force attacks).
- Derived key length: `256` bits.

In code (simplified):

```java
PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN);
SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
byte[] hash = factory.generateSecret(spec).getEncoded();
```

### Setting the master password

On first setup or when changing the master password:

1. A new random salt is generated.
2. PBKDF2 is used to derive a hash from the password + salt.
3. Both values are Base64‑encoded.
4. The string `saltBase64 + ":" + hashBase64` is written to `master.config`.

This happens in `setMasterPassword(String password)`.

### Verifying the master password

When the user logs in:

1. `isMasterPasswordSet()` checks that `master.config` exists and is non‑empty.
2. The stored line is read and split at `:` into `saltBase64` and `hashBase64`.
3. Both are Base64‑decoded.
4. The input password is hashed again with PBKDF2 using **the same salt**.
5. The new hash and stored hash are compared using `Arrays.equals`.

Only if they match the login is considered valid.

### Security implications

- Even if `master.config` is read by an attacker, they only receive:
  - A random salt.
  - A PBKDF2‑HMAC‑SHA‑256 hash.
- Brute‑forcing is intentionally slowed via 65k iterations.
- The salt prevents pre‑computed rainbow table attacks.

Note: This protects the **master password** on disk. The actual password data is protected separately via AES‑GCM (next section).

---

## CryptoUtils – Encryption of password entries

`CryptoUtils` is responsible for encrypting and decrypting the entire list of password entries.  
It never stores keys or plaintext on disk; only ciphertext plus the parameters needed for decryption.

### Algorithms and parameters

- Symmetric cipher: `AES/GCM/NoPadding` (AES‑GCM).
- Authentication tag length: 128 bits.
- IV (nonce) length: 12 bytes (standard for GCM).
- Salt length: 16 bytes (for key derivation).
- AES key size: 256 bits.
- Key derivation: PBKDF2 with HMAC‑SHA‑256 and 65,536 iterations (same as `AuthManager`).

### Key derivation

To encrypt or decrypt, we need an AES key derived from the current **master password** and a random salt.

`getSecretKey(String password, byte[] salt)`:

1. Uses `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`.
2. Builds a `PBEKeySpec` with:
   - Password characters.
   - Salt.
   - `ITERATION_COUNT` (65536).
   - `AES_KEY_BIT` (256).
3. Generates a temporary secret key.
4. Wraps the raw bytes into a `SecretKeySpec` for AES.

Result: a 256‑bit AES key that is **deterministic** for the same (password, salt) pair.

### Encrypting entries

`encrypt(String passwordToStore, String masterPassword)` does the following:

1. **Generate random salt** (16 bytes) and **random IV** (12 bytes).
2. Derive AES key = PBKDF2(masterPassword, salt).
3. Create `Cipher` instance for `AES/GCM/NoPadding` in ENCRYPT_MODE with key + IV.
4. Encrypt plaintext (`passwordToStore`) using UTF‑8 bytes.
5. Concatenate `iv + salt + ciphertext` into a single byte buffer:

   ```text
   [ IV (12 bytes) | SALT (16 bytes) | CIPHERTEXT (N bytes incl. auth tag) ]
   ```

6. Base64‑encode the combined buffer and return it as a string.

This encoded string is what gets written to `passwords.enc` by `PasswordManagerController.saveEntries()`.

### Decrypting entries

`decrypt(String encodedString, String masterPassword)` reverses the process:

1. Base64‑decode the string back to bytes.
2. Wrap the bytes in a `ByteBuffer`.
3. Read the first 12 bytes as IV.
4. Read the next 16 bytes as salt.
5. Read the remaining bytes as ciphertext.
6. Re‑derive the same AES key with PBKDF2(masterPassword, salt).
7. Initialize `Cipher` in DECRYPT_MODE with the key and IV.
8. Call `doFinal` on the ciphertext; GCM verifies the authentication tag.
9. Convert the resulting plaintext bytes back to a UTF‑8 string.

If the master password is wrong or the data has been tampered with, decryption fails and an exception is thrown.

### Why AES‑GCM

- **Confidentiality**: Passwords are not readable without the correct AES key.
- **Integrity & authenticity**: GCM includes an authentication tag, so modifications to the ciphertext are detected.
- **Per‑encryption randomness**: 
  - A new IV and salt are generated for each encryption.
  - Even if you encrypt the same data twice with the same master password, the ciphertext will be different.

### Data format on disk (`passwords.enc`)

High‑level view:

1. `PasswordManagerController` serializes all entries to a small JSON string.
2. `CryptoUtils.encrypt(json, masterPassword)` returns an opaque Base64 string.
3. That string is written to:

```text
%APPDATA%\\PasswordGenerator\\passwords.enc
```

To read:

1. `Files.readString(DATA_PATH)` reads the Base64 string.
2. `CryptoUtils.decrypt(encrypted, masterPassword)` returns the JSON text.
3. The JSON is parsed back into `PasswordEntry` objects.

At no point is unencrypted JSON stored on disk.

---

## Security notes and limitations

- **Master password strength** is critical. The PBKDF2 parameters help, but a weak master password can still be brute‑forced.
- The app keeps the master password in memory while the manager is open and optionally for a short cached login window.
- Clipboard operations are convenient but can be observed by other software on the system; use with care.
- JSON parsing and serialization are implemented manually for a tiny, controlled format to avoid extra dependencies.

---

## Building and running

- This is a standard JavaFX project.
- Make sure JavaFX dependencies are configured for your build system (Maven/Gradle/IDE).
- Run the `Main` class as a JavaFX application.

---

## Future improvements (ideas)

- Add search / filter functionality for entries.
- Support multiple vaults or profiles.
- Add automatic lock after inactivity.
- Integrate a stronger password policy checker for the master password.

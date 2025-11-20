package com.example.password_generator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Controller for the Password Manager window (table with all stored entries)
public class PasswordManagerController {

    // Table + columns for displaying saved entries
    @FXML private TableView<PasswordEntry> passwordTable;
    @FXML private TableColumn<PasswordEntry, String> colService;
    @FXML private TableColumn<PasswordEntry, String> colUsername;
    @FXML private TableColumn<PasswordEntry, String> colPassword;

    // Input fields for a single entry
    @FXML private TextField serviceField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private CheckBox showPasswordCheckBox;

    // Status label for user feedback + current master password
    @FXML private Label statusMessage;
    private String masterPassword;

    // Menu option that controls whether passwords are shown or masked in the table
    @FXML private CheckMenuItem showPasswordsInTableMenuItem;

    // Internal flag: true = show plaintext in table, false = show ******
    private boolean showPasswordsInTable = false;

    private final AuthManager authManager = new AuthManager();

    // Path where the encrypted password data is stored
    private static final Path DATA_PATH = Path.of(
            System.getenv("APPDATA"),
            "PasswordGenerator",
            "passwords.enc"
    );

    // List holding the data for the table
    private final ObservableList<PasswordEntry> passwordData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Link the table columns to the properties of the PasswordEntry class
        colService.setCellValueFactory(new PropertyValueFactory<>("service"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colPassword.setCellValueFactory(new PropertyValueFactory<>("password"));

        // Custom cell factory for password column: mask passwords unless the filter option is enabled
        colPassword.setCellFactory(column -> new TableCell<PasswordEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(showPasswordsInTable ? item : "******");
                }
            }
        });

        // Bind data list to the table view
        passwordTable.setItems(passwordData);

        // Update text fields when a table row is selected
        passwordTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                serviceField.setText(newSelection.getService());
                usernameField.setText(newSelection.getUsername());
                passwordField.setText(newSelection.getPassword());
                if (showPasswordCheckBox.isSelected()) {
                    passwordVisibleField.setText(newSelection.getPassword());
                }
            } else {
                serviceField.clear();
                usernameField.clear();
                passwordField.clear();
                passwordVisibleField.clear();
            }
        });
    }

    @FXML
    // Create a new entry from the input fields and persist it
    protected void onAddEntryClick() {
        String service = serviceField.getText();
        String user = usernameField.getText();
        String pass = passwordField.getText();

        // Validate input
        if (service.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            statusMessage.setText("Please fill out all Fields!");
            statusMessage.setStyle("-fx-text-fill: red;");
            return;
        }

        // Add new entry to the list
        passwordData.add(new PasswordEntry(service, user, pass));

        saveEntries();

        // Clear input fields after adding
        serviceField.clear();
        usernameField.clear();
        passwordField.clear();
        passwordVisibleField.clear();

        statusMessage.setText("Entry has been successfully added.");
        statusMessage.setStyle("-fx-text-fill: green;");
    }

    @FXML
    // Remove the currently selected entry from the table and persist
    protected void onDeleteEntryClick() {
        // Get the currently selected item
        PasswordEntry selectedItem = passwordTable.getSelectionModel().getSelectedItem();

        if (selectedItem != null) {
            passwordData.remove(selectedItem);
            statusMessage.setText("Entry successfully deleted.");
            saveEntries();
        } else {
            statusMessage.setText("No entry found.");
            statusMessage.setStyle("-fx-text-fill: red;");
        }
    }

    @FXML
    // Update the currently selected entry with the values from the input fields
    protected void onEditEntryClick() {
        PasswordEntry selectedItem = passwordTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            statusMessage.setText("No entry selected.");
            statusMessage.setStyle("-fx-text-fill: red;");
            return;
        }

        String service = serviceField.getText();
        String user = usernameField.getText();
        String pass = passwordField.getText();

        if (service.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            statusMessage.setText("Please fill out all fields!");
            statusMessage.setStyle("-fx-text-fill: red;");
            return;
        }

        int index = passwordData.indexOf(selectedItem);
        if (index >= 0) {
            passwordData.set(index, new PasswordEntry(service, user, pass));
            statusMessage.setText("Updated entry.");
            statusMessage.setStyle("-fx-text-fill: green;");
            saveEntries();
        }
    }

    @FXML
    // Copies the password of the selected entry to the system clipboard
    protected void onCopyPasswordClick() {
        PasswordEntry selectedItem = passwordTable.getSelectionModel().getSelectedItem();

        if (selectedItem != null) {
            // Copy password to system clipboard
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedItem.getPassword());
            clipboard.setContent(content);

            statusMessage.setText("Password has been copied to your clipboard!");
            statusMessage.setStyle("-fx-text-fill: green;");
        }
    }

    @FXML
    // Closes the Password Manager window
    protected void onCloseClick() {
        // Get the current stage (window) and close it
        Stage stage = (Stage) passwordTable.getScene().getWindow();
        applyWindowIcon(stage);
        stage.close();
    }

    @FXML
    // Closes the manager window and returns control to the generator window
    protected void onOpenGeneratorClick() {
        // Logic to close the manager window (returning to generator if it's still open)
        Stage stage = (Stage) passwordTable.getScene().getWindow();
        applyWindowIcon(stage);
        stage.close();
    }

    @FXML
    protected void onAboutClick() {
        String securityInfo = "Security Architecture:\n" +
                "• Encryption: AES-256 in GCM Mode (Authenticated Encryption)\n" +
                "• Key Protection: Strong Key Derivation (PBKDF2) with random Salt\n" +
                "• Integrity: Protected against manipulation and rainbow tables";
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Password Generator");
        alert.setHeaderText(null);
        alert.setContentText("Password Generator with integrated Password Manager\n" +
                "Created by Justin Kirsch\n" +
                "\n" +
                securityInfo);
        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        applyWindowIcon(alertStage);
        alert.showAndWait();
    }

    @FXML
    protected void onChangeMasterPasswordClick() {
        Stage dialog = new Stage();
        dialog.setTitle("Change master password");
        dialog.initModality(Modality.APPLICATION_MODAL);
        applyWindowIcon(dialog);

        Label lblOld = new Label("Current Master-Password:");
        PasswordField pfOld = new PasswordField();

        Label lblNew = new Label("New Master-Password:");
        PasswordField pfNew = new PasswordField();

        Label lblConfirm = new Label("Confirm new Master-Password:");
        PasswordField pfConfirm = new PasswordField();

        Button btnSave = new Button("Save");
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red;");

        btnSave.setOnAction(e -> {
            String oldPw = pfOld.getText();
            String newPw = pfNew.getText();
            String confirmPw = pfConfirm.getText();

            if (oldPw == null || oldPw.isEmpty() || newPw == null || newPw.isEmpty() || confirmPw == null || confirmPw.isEmpty()) {
                lblError.setText("Please fill out all Fields!");
                return;
            }

            if (!newPw.equals(confirmPw)) {
                lblError.setText("The new passwords do not match.");
                return;
            }

            try {
                if (!authManager.verifyMasterPassword(oldPw)) {
                    lblError.setText("Current Master-Password is wrong.");
                    pfOld.clear();
                    return;
                }

                authManager.setMasterPassword(newPw);
                this.masterPassword = newPw;
                saveEntries();

                statusMessage.setText("Master-Password has been successfully updated.");
                statusMessage.setStyle("-fx-text-fill: green;");
                dialog.close();
            } catch (Exception ex) {
                lblError.setText("Error when changing the master password.");
                statusMessage.setText("Error when changing the master password.");
                statusMessage.setStyle("-fx-text-fill: red;");
                ex.printStackTrace();
            }
        });

        VBox layout = new VBox(10, lblOld, pfOld, lblNew, pfNew, lblConfirm, pfConfirm, btnSave, lblError);
        layout.setAlignment(javafx.geometry.Pos.CENTER);
        layout.setPadding(new javafx.geometry.Insets(20));

        dialog.setScene(new javafx.scene.Scene(layout, 350, 300));

        Stage parentStage = (Stage) passwordTable.getScene().getWindow();
        if (parentStage != null) {
            dialog.setX(parentStage.getX() + (parentStage.getWidth() / 2) - 175);
            dialog.setY(parentStage.getY() + (parentStage.getHeight() / 2) - 150);
        }

        dialog.showAndWait();
    }

    @FXML
    // Pre-fills the password field with a password generated in the main window
    public void setGeneratedPassword(String password) {
        this.passwordField.setText(password);
        if (showPasswordCheckBox != null && showPasswordCheckBox.isSelected()) {
            this.passwordVisibleField.setText(password);
        }
    }

    @FXML
    protected void onShowPasswordToggle() {
        if (showPasswordCheckBox.isSelected()) {
            // Show password as plain text
            passwordVisibleField.setText(passwordField.getText());
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
        } else {
            // Hide password again
            passwordField.setText(passwordVisibleField.getText());
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
        }
    }

    @FXML
    // Toggles whether the password column in the table shows real passwords or ******
    protected void onToggleShowPasswordsInTable() {
        if (showPasswordsInTableMenuItem != null) {
            showPasswordsInTable = showPasswordsInTableMenuItem.isSelected();
            // Force table to redraw cells with the new masking state
            passwordTable.refresh();
        }
    }

    // Receives the master password from MainController and loads existing entries
    public void setMasterPassword(String masterPassword) {
        this.masterPassword = masterPassword;
        loadEntries();
    }

    // Serializes all entries, encrypts them with the master password, and writes them to disk
    private void saveEntries() {
        if (masterPassword == null || masterPassword.isEmpty()) {
            return;
        }

        try {
            String json = serializeEntriesToJson();
            String encrypted = CryptoUtils.encrypt(json, masterPassword);

            Files.createDirectories(DATA_PATH.getParent());
            Files.writeString(DATA_PATH, encrypted);
        } catch (Exception e) {
            statusMessage.setText("Error: Could not save the entries.");
            statusMessage.setStyle("-fx-text-fill: red;");
            e.printStackTrace();
        }
    }

    // Reads, decrypts and deserializes all stored entries into the table
    private void loadEntries() {
        if (masterPassword == null || masterPassword.isEmpty()) {
            return;
        }

        try {
            if (!Files.exists(DATA_PATH)) {
                return;
            }

            String encrypted = Files.readString(DATA_PATH);
            if (encrypted == null || encrypted.isEmpty()) {
                return;
            }

            String json = CryptoUtils.decrypt(encrypted, masterPassword);
            List<PasswordEntry> entries = parseEntriesFromJson(json);

            passwordData.clear();
            passwordData.addAll(entries);
        } catch (Exception e) {
            statusMessage.setText("Failed to load entries.");
            statusMessage.setStyle("-fx-text-fill: red;");
            e.printStackTrace();
        }
    }

    // Converts the current ObservableList to a minimal JSON array
    private String serializeEntriesToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < passwordData.size(); i++) {
            PasswordEntry entry = passwordData.get(i);
            sb.append("{");
            sb.append("\"service\":\"").append(escapeJson(entry.getService())).append("\",");
            sb.append("\"username\":\"").append(escapeJson(entry.getUsername())).append("\",");
            sb.append("\"password\":\"").append(escapeJson(entry.getPassword())).append("\"");
            sb.append("}");
            if (i < passwordData.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // Parses the minimal JSON format back into PasswordEntry objects
    private List<PasswordEntry> parseEntriesFromJson(String json) {
        List<PasswordEntry> entries = new ArrayList<>();
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return entries;
        }

        String content = trimmed.substring(1, trimmed.length() - 1).trim();
        if (content.isEmpty()) {
            return entries;
        }

        String[] objects = content.split("(?<=\\}),(?=\\{)");
        for (String obj : objects) {
            String o = obj.trim();
            if (!o.startsWith("{") || !o.endsWith("}")) {
                continue;
            }
            o = o.substring(1, o.length() - 1);

            String service = extractJsonField(o, "service");
            String username = extractJsonField(o, "username");
            String password = extractJsonField(o, "password");

            if (service != null && username != null && password != null) {
                entries.add(new PasswordEntry(service, username, password));
            }
        }

        return entries;
    }

    // Extracts a single string field from a very small JSON object (no full JSON parser on purpose)
    private String extractJsonField(String objectContent, String fieldName) {
        String pattern = "\"" + fieldName + "\"" + ":\"";
        int start = objectContent.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start += pattern.length();
        int end = start;
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        while (end < objectContent.length()) {
            char c = objectContent.charAt(end);
            if (escaped) {
                value.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
            end++;
        }
        return unescapeJson(value.toString());
    }

    // Escapes characters that would break the simple JSON representation
    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    // Reverses escapeJson so we get the original strings back
    private String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private void applyWindowIcon(Stage stage) {
        stage.getIcons().add(
                new Image(getClass().getResourceAsStream("/icons/icon.png"))
        );
    }
}
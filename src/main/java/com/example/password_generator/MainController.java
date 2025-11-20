package com.example.password_generator;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Insets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Controller for the main password generator window and the entry point to the Password Manager
public class MainController {
    // Checkboxes for the four character groups (A-Z, a-z, 0-9, symbols)
    public CheckBox checkBox1;
    public CheckBox checkBox2;
    public CheckBox checkBox3;
    public CheckBox checkBox4;

    // Slider + label + output area for the generated password
    public Slider passwordLengthSlider;
    public Label passwordLength;
    public TextArea generatedPasswordTextArea;

    // Current slider value and flags reflecting which character groups are enabled
    double currentSliderValue;
    boolean upper, lower, numbers, symbols;
    @FXML
    private Label successMessage;
    private final AuthManager authManager = new AuthManager();

    // In-memory session cache for the master password (valid while app is running)
    private String cachedMasterPassword;
    private long lastMasterLoginMillis = 0L;

    // Character pools used for password generation
    private final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private final String DIGITS = "0123456789";
    private final String SYMBOLS = "!@#$%^&*()-_=+<>?";

    @FXML
    protected void onGeneratePasswordButtonClick() {
        if(!upper && !lower && !numbers && !symbols) {
            successMessage.setText("Please select at least one character type!");
            return;
        }

        // Creating charPools List
        List<String> charPools = new ArrayList<>();

        if(upper) charPools.add(UPPERCASE);
        if(lower) charPools.add(LOWERCASE);
        if(numbers) charPools.add(DIGITS);
        if(symbols) charPools.add(SYMBOLS);

        // Generate random password
        int length = (int) passwordLengthSlider.getValue();
        String password = generateRandomPassword(charPools, length);

        // Show generated random password in TextArea
        generatedPasswordTextArea.setText(password);
        successMessage.setText("Your password has been generated!");
    }

    // Core generation logic: builds a random password from the selected character pools
    private String generateRandomPassword(List<String> charPools, int length) {

        Random random = new Random();
        StringBuilder allChars = new StringBuilder();

        // Merge all permitted characters
        for (String pool : charPools) {
            allChars.append(pool);
        }

        List<Character> passwordChars = new ArrayList<>();

        // 1. First, add at least 1 character per activated group
        for (String pool : charPools) {
            int index = random.nextInt(pool.length());
            passwordChars.add(pool.charAt(index));
        }

        // 2. Fill remaining characters randomly
        while (passwordChars.size() < length) {
            int index = random.nextInt(allChars.length());
            passwordChars.add(allChars.charAt(index));
        }

        // 3. Mix password characters (Fisher-Yates)
        for (int i = passwordChars.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordChars.get(i);
            passwordChars.set(i, passwordChars.get(j));
            passwordChars.set(j, temp);
        }

        // 4. Transform to string
        StringBuilder password = new StringBuilder();
        for (char c : passwordChars) {
            password.append(c);
        }

        return password.toString();
    }


    @FXML
    protected void onSliderChange() {
        currentSliderValue = passwordLengthSlider.getValue();
        String currentSliderValueAsString = Double.toString(currentSliderValue);

        passwordLength.setText(currentSliderValueAsString);
    }

    @FXML
    public void onCheckboxChange(ActionEvent actionEvent) {
        upper = checkBox1.isSelected();
        lower = checkBox2.isSelected();
        numbers = checkBox3.isSelected();
        symbols = checkBox4.isSelected();
    }

    @FXML
    public void onCopyButtonClick(ActionEvent actionEvent) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(generatedPasswordTextArea.getText());
        clipboard.setContent(content);
    }

    @FXML
    public void onCloseClick() {
        Platform.exit();
    }

    @FXML
    protected void onAboutClick() {
        String securityInfo = "Security Architecture:\n" +
                "• Encryption: AES-256 in GCM Mode (Authenticated Encryption)\n" +
                "• Key Protection: Strong Key Derivation (PBKDF2) with random Salt\n" +
                "• Integrity: Protected against manipulation and rainbow tables";
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Über Password Generator");
        alert.setHeaderText(null);
        alert.setContentText(
                "Password Generator with integrated Password Manager\n" +
                        "Created by Justin Kirsch\n" +
                        "\n" +
                        securityInfo);
        alert.showAndWait();
    }

    @FXML
    // Opens the Password Manager window and passes the master password + optional generated password
    public void openManagerWindow(String passwordToTransfer, String masterPassword) {
        try {
            Stage managerStage = new Stage();
            managerStage.setTitle("Password Manager");

            FXMLLoader managerFxmlLoader = new FXMLLoader(Main.class.getResource("password_manager_ui.fxml"));
            Scene scene = new Scene(managerFxmlLoader.load(), 490, 430);

            PasswordManagerController controller = managerFxmlLoader.getController();

            // --- CRITICAL: Pass the Master Password to the controller ---
            // (You must create this method in PasswordManagerController)
            if (masterPassword != null) {
                controller.setMasterPassword(masterPassword);
            }

            // Pass the generated password if available
            if(passwordToTransfer != null && !passwordToTransfer.isEmpty()) {
                controller.setGeneratedPassword(passwordToTransfer);
            }

            // Window positioning and styling
            Stage currentStage = (Stage) passwordLengthSlider.getScene().getWindow();
            managerStage.getIcons().add(
                    new Image(getClass().getResourceAsStream("/icons/icon.png"))
            );

            managerStage.setX(currentStage.getX() + currentStage.getWidth());
            managerStage.setY(currentStage.getY());

            managerStage.setScene(scene);
            managerStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onOpenManagerClick() {
        initiateLoginFlow(null);
    }

    // Decides whether to reuse a cached master password, show login, or run first-time setup
    private void initiateLoginFlow(String passwordToTransfer) {
        boolean isMasterPasswordSet = AuthManager.isMasterPasswordSet();

        // If a master password is set and we have a recent successful login (within 5 minutes),
        // reuse the cached master password without showing the login dialog.
        if (isMasterPasswordSet && cachedMasterPassword != null) {
            long now = System.currentTimeMillis();
            long fiveMinutesMillis = 5 * 60 * 1000L;
            if (now - lastMasterLoginMillis <= fiveMinutesMillis) {
                openManagerWindow(passwordToTransfer, cachedMasterPassword);
                return;
            }
        }

        if (isMasterPasswordSet) {
            showLoginDialog(passwordToTransfer);
        } else {
            showSetupDialog(passwordToTransfer);
        }
    }

    @FXML
    public void onCreateNewUserClick() {
        initiateLoginFlow(generatedPasswordTextArea.getText());
    }

    // --- DIALOG: First Time Setup ---
    // --- DIALOG: First Time Setup ---
    // Asks the user to create and confirm a new master password, then opens the manager
    private void showSetupDialog(String passwordToTransfer) {
        Stage setupStage = new Stage();
        setupStage.setTitle("Setup Master Password");
        // Blocks interaction with the main window until closed
        setupStage.initModality(Modality.APPLICATION_MODAL);

        Label lbl = new Label("Create a new Master Password:");
        PasswordField pf = new PasswordField();
        pf.setPromptText("New Password");
        TextField pfVisible = new TextField();
        pfVisible.setManaged(false);
        pfVisible.setVisible(false);

        PasswordField pfConfirm = new PasswordField();
        pfConfirm.setPromptText("Confirm Password");
        TextField pfConfirmVisible = new TextField();
        pfConfirmVisible.setManaged(false);
        pfConfirmVisible.setVisible(false);

        CheckBox showPasswordCheckBox = new CheckBox("Show password");

        Button btnSave = new Button("Save & Start");
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red;");

        btnSave.setOnAction(e -> {
            String newPassword = showPasswordCheckBox.isSelected()
                    ? pfVisible.getText()
                    : pf.getText();
            String confirmPassword = showPasswordCheckBox.isSelected()
                    ? pfConfirmVisible.getText()
                    : pfConfirm.getText();
            // 1. Validation
            if (newPassword.isEmpty()) {
                lblError.setText("Password cannot be empty.");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                lblError.setText("Passwords do not match.");
                return;
            }

            try {
                // 2. Save the hash to the file
                authManager.setMasterPassword(newPassword);

                // Cache master password and login time for 3-minute session reuse
                cachedMasterPassword = newPassword;
                lastMasterLoginMillis = System.currentTimeMillis();
                setupStage.close();

                // 3. Open Manager and pass the raw password (key) + optional generated password
                openManagerWindow(passwordToTransfer, newPassword);
            } catch (Exception ex) {
                lblError.setText("Error saving password.");
                ex.printStackTrace();
            }
        });

        showPasswordCheckBox.setOnAction(e -> {
            if (showPasswordCheckBox.isSelected()) {
                pfVisible.setText(pf.getText());
                pfVisible.setVisible(true);
                pfVisible.setManaged(true);
                pf.setVisible(false);
                pf.setManaged(false);

                pfConfirmVisible.setText(pfConfirm.getText());
                pfConfirmVisible.setVisible(true);
                pfConfirmVisible.setManaged(true);
                pfConfirm.setVisible(false);
                pfConfirm.setManaged(false);
            } else {
                pf.setText(pfVisible.getText());
                pf.setVisible(true);
                pf.setManaged(true);
                pfVisible.setVisible(false);
                pfVisible.setManaged(false);

                pfConfirm.setText(pfConfirmVisible.getText());
                pfConfirm.setVisible(true);
                pfConfirm.setManaged(true);
                pfConfirmVisible.setVisible(false);
                pfConfirmVisible.setManaged(false);
            }
        });

        VBox layout = new VBox(10, lbl, pf, pfVisible, pfConfirm, pfConfirmVisible, showPasswordCheckBox, btnSave, lblError);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        setupStage.setScene(new Scene(layout, 300, 250));

        centerStage(setupStage);
        setupStage.show();
    }

    // --- DIALOG: Login ---
    // Prompts for the existing master password and optionally caches it for a short session
    private void showLoginDialog(String passwordToTransfer) {
        Stage loginStage = new Stage();
        loginStage.setTitle("Login");
        loginStage.initModality(Modality.APPLICATION_MODAL);

        Label lbl = new Label("Enter Master Password:");
        PasswordField pf = new PasswordField();
        TextField pfVisible = new TextField();
        pfVisible.setManaged(false);
        pfVisible.setVisible(false);

        CheckBox showPasswordCheckBox = new CheckBox("Show password");
        CheckBox rememberCheckBox = new CheckBox("Remember me for 5 minutes");
        Button btnLogin = new Button("Login");
        Label lblError = new Label();
        lblError.setStyle("-fx-text-fill: red;");

        // Logic to execute upon login attempt
        Runnable doLogin = () -> {
            try {
                String enteredPassword = showPasswordCheckBox.isSelected()
                        ? pfVisible.getText()
                        : pf.getText();
                // 1. Verify hash
                if (authManager.verifyMasterPassword(enteredPassword)) {
                    // Optionally cache master password and login time for 5-minute session reuse
                    if (rememberCheckBox.isSelected()) {
                        cachedMasterPassword = enteredPassword;
                        lastMasterLoginMillis = System.currentTimeMillis();
                    }

                    loginStage.close();
                    // 2. Open Manager with the correct key
                    openManagerWindow(passwordToTransfer, enteredPassword);
                } else {
                    lblError.setText("The entered Master password does not\nmatch. Please Try Again!");
                    pf.clear();
                    pfVisible.clear();
                }
            } catch (Exception ex) {
                lblError.setText("System error during verification.");
                ex.printStackTrace();
            }
        };

        btnLogin.setOnAction(e -> doLogin.run());
        pf.setOnAction(e -> doLogin.run()); // Allow login via Enter key

        showPasswordCheckBox.setOnAction(e -> {
            if (showPasswordCheckBox.isSelected()) {
                pfVisible.setText(pf.getText());
                pfVisible.setVisible(true);
                pfVisible.setManaged(true);
                pf.setVisible(false);
                pf.setManaged(false);
            } else {
                pf.setText(pfVisible.getText());
                pf.setVisible(true);
                pf.setManaged(true);
                pfVisible.setVisible(false);
                pfVisible.setManaged(false);
            }
        });

        // Group both checkboxes so they align nicely on the left side
        VBox checkBoxContainer = new VBox(5, showPasswordCheckBox, rememberCheckBox);
        checkBoxContainer.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(10, lbl, pf, pfVisible, checkBoxContainer, btnLogin, lblError);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        loginStage.setScene(new Scene(layout, 300, 200));

        centerStage(loginStage);
        loginStage.show();
    }

    // Centers a child dialog relative to the main generator window
    private void centerStage(Stage stage) {
        Stage mainStage = (Stage) passwordLengthSlider.getScene().getWindow();
        if (mainStage != null) {
            stage.setX(mainStage.getX() + (mainStage.getWidth() / 2) - 150);
            stage.setY(mainStage.getY() + (mainStage.getHeight() / 2) - 100);
        }
    }


}
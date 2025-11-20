package com.example.password_generator;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

// JavaFX application entry point
public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Loading the fxml-file
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("password_generator_ui.fxml"));

        // Creating the Scene
        Scene scene = new Scene(fxmlLoader.load(), 500, 300);

        // implementing CSS
        scene.getStylesheets().add(getClass().getResource("/styles/style.css").toExternalForm());
        stage.getIcons().add(
                new Image(getClass().getResourceAsStream("/icons/icon.png"))
        );
        stage.setMinWidth(500);
        stage.setMinHeight(440);
        stage.setMaxWidth(500);
        stage.setMaxHeight(440);

        // Window-Title
        stage.setTitle("Password Generator by Kirsch");

        // Setting the Scene
        stage.setScene(scene);

        // Showing Window (Stage)
        stage.show();
    }

    // Standard main() that launches the JavaFX application
    public static void main(String[] args) {
        launch();
    }
}

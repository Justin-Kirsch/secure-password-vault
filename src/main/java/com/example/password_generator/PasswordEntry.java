package com.example.password_generator;

import javafx.beans.property.SimpleStringProperty;

// Simple model class representing one row in the Password Manager table
public class PasswordEntry {
    private final SimpleStringProperty service;
    private final SimpleStringProperty username;
    private final SimpleStringProperty password;

    // Construct a new entry with the three display fields
    public PasswordEntry(String service, String username, String password) {
        this.service = new SimpleStringProperty(service);
        this.username = new SimpleStringProperty(username);
        this.password = new SimpleStringProperty(password);
    }

    public String getService() { return service.get(); }
    public String getUsername() { return username.get(); }
    public String getPassword() { return password.get(); }

    // Getter für Properties (wichtig für TableView)
    public SimpleStringProperty serviceProperty() { return service; }
    public SimpleStringProperty usernameProperty() { return username; }
    public SimpleStringProperty passwordProperty() { return password; }
}
package com.chat.client.view;

import com.chat.client.model.AppConfig;
import com.chat.client.model.ChatConnection;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/** JavaFX client: login screen, then the chat screen. Networking lives in {@link ChatConnection}. */
public class ClientApp extends Application {
    private String host;
    private int port;
    private ChatView activeView;

    @Override
    public void start(Stage stage) {
        Properties cfg = AppConfig.load("config.properties");
        host = cfg.getProperty("server.host", "localhost").trim();
        port = AppConfig.getInt(cfg, "server.port", 3000);

        // Command-line arguments override the config file.
        List<String> args = getParameters().getRaw();
        if (args.size() >= 2) {
            host = args.get(0);
            try {
                port = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args.get(1));
                Platform.exit();
                return;
            }
        }

        stage.setTitle("Group Chat Client");
        stage.setMinWidth(420);
        stage.setMinHeight(300);
        stage.setOnCloseRequest(e -> {
            if (activeView != null) activeView.shutdown();
        });
        showLogin(stage);
        stage.show();
    }

    private void showLogin(Stage stage) {
        activeView = null;

        Label title = new Label("Join the chat");
        title.getStyleClass().add("title");
        Label server = new Label("Server: " + host + ":" + port);
        server.getStyleClass().add("subtitle");

        TextField nameField = new TextField();
        nameField.setPromptText("Username (leave empty for READ-ONLY MODE)");
        nameField.setMaxWidth(320);

        Button join = new Button("JOIN");
        join.getStyleClass().add("send-button");
        Label message = new Label();
        message.getStyleClass().add("error-label");
        message.setWrapText(true);
        message.setMaxWidth(320);

        Runnable doJoin = () -> {
            String name = nameField.getText().trim();
            join.setDisable(true);
            nameField.setDisable(true);
            message.setText("Connecting to " + host + ":" + port + " ...");

            ChatView view = new ChatView(name, () -> showLogin(stage));
            ChatConnection conn = new ChatConnection(host, port, name, view);
            view.bind(conn);

            Thread.ofVirtual().start(() -> {
                try {
                    conn.connect();
                    Platform.runLater(() -> {
                        activeView = view;
                        stage.setScene(createScene(view, 560, 640));
                        stage.sizeToScene();
                        stage.centerOnScreen();
                    });
                } catch (IOException ex) {
                    Platform.runLater(() -> {
                        message.setText("Could not join: " + ex.getMessage());
                        join.setDisable(false);
                        nameField.setDisable(false);
                    });
                }
            });
        };
        join.setOnAction(e -> doJoin.run());
        nameField.setOnAction(e -> doJoin.run());

        VBox root = new VBox(12, title, server, nameField, join, message);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("login-root");

        stage.setScene(createScene(root, 440, 320));
        stage.sizeToScene();
        stage.centerOnScreen();
        Platform.runLater(nameField::requestFocus);
    }

    private Scene createScene(Parent root, double w, double h) {
        Scene scene = new Scene(root, w, h);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        return scene;
    }
}

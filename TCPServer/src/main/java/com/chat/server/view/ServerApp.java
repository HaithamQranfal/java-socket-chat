package com.chat.server.view;

import com.chat.server.model.AppConfig;
import com.chat.server.model.ChatServer;
import com.chat.server.model.ServerListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

/** JavaFX view for the server. All business logic lives in {@link ChatServer}. */
public class ServerApp extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final Map<String, Color> colors = new HashMap<>();
    private final TextArea logArea = new TextArea();
    private final Label countLabel = new Label("0 users connected");
    private ChatServer server;

    @Override
    public void start(Stage stage) {
        Properties cfg = AppConfig.load("config.properties");
        int port = AppConfig.getInt(cfg, "server.port", 3000);

        Label title = new Label("TCP Chat Server  -  port " + port);
        title.getStyleClass().add("title");
        countLabel.getStyleClass().add("subtitle");

        ListView<String> userView = new ListView<>(users);
        userView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                Color c = colors.computeIfAbsent(item, k -> randomColor());
                setText(item);
                setStyle("-fx-background-color: " + toHex(c) + "; -fx-text-fill: #1b1b1b;");
            }
        });

        logArea.setEditable(false);
        logArea.setWrapText(true);

        Label usersHeader = new Label("Connected users");
        usersHeader.getStyleClass().add("section");
        Label logHeader = new Label("Activity log");
        logHeader.getStyleClass().add("section");

        GridPane root = new GridPane();
        root.getStyleClass().add("server-root");
        root.setPadding(new Insets(12));
        root.setHgap(10);
        root.setVgap(8);

        ColumnConstraints c0 = new ColumnConstraints(230);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        root.getColumnConstraints().addAll(c0, c1);
        RowConstraints r0 = new RowConstraints();
        RowConstraints r1 = new RowConstraints();
        RowConstraints r2 = new RowConstraints();
        r2.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(r0, r1, r2);

        root.add(title, 0, 0, 2, 1);
        root.add(new HBox(8, usersHeader, countLabel), 0, 1);
        root.add(logHeader, 1, 1);
        root.add(userView, 0, 2);
        root.add(logArea, 1, 2);

        Scene scene = new Scene(root, 760, 480);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        stage.setTitle("TCP Chat Server");
        stage.setScene(scene);
        stage.show();

        ServerListener listener = new ServerListener() {
            @Override
            public void onLog(String message) {
                Platform.runLater(() ->
                        logArea.appendText("[" + LocalTime.now().format(TIME) + "] " + message + "\n"));
            }

            @Override
            public void onUserJoined(String username) {
                Platform.runLater(() -> {
                    if (!users.contains(username)) users.add(username);
                    updateCount();
                });
            }

            @Override
            public void onUserLeft(String username) {
                Platform.runLater(() -> {
                    users.remove(username);
                    colors.remove(username);
                    updateCount();
                });
            }
        };

        try {
            server = new ChatServer(port, listener);
            server.start();
        } catch (IOException e) {
            listener.onLog("Could not start server on port " + port + ": " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (server != null) server.stop();
    }

    private void updateCount() {
        countLabel.setText(users.size() + (users.size() == 1 ? " user" : " users") + " connected");
    }

    private static Color randomColor() {
        return Color.hsb(ThreadLocalRandom.current().nextDouble(360), 0.40, 0.97);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}

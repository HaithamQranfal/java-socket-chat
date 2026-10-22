package com.chat.client.view;

import com.chat.client.model.ChatConnection;
import com.chat.client.model.ChatListener;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

/** Chat screen. Implements {@link ChatListener} and hops to the FX thread for every update. */
public class ChatView extends GridPane implements ChatListener {
    private static final Color ONLINE = Color.web("#2ecc71");
    private static final Color OFFLINE = Color.web("#95a5a6");

    private final boolean readOnly;
    private final Runnable onBackToLogin;

    private final Circle statusDot = new Circle(6, ONLINE);
    private final Label statusLabel = new Label("Online");
    private final Button leaveButton = new Button("LEAVE");
    private final TextArea chatArea = new TextArea();
    private final TextArea input = new TextArea();
    private final Button sendButton = new Button("SEND");

    private ChatConnection connection;
    private boolean online = true;

    public ChatView(String username, Runnable onBackToLogin) {
        this.readOnly = username == null || username.isBlank();
        this.onBackToLogin = onBackToLogin;

        getStyleClass().add("chat-root");
        setPadding(new Insets(12));
        setHgap(8);
        setVgap(8);

        Label title = new Label("Group Chat");
        title.getStyleClass().add("title");
        Label identity = new Label(readOnly ? "READ-ONLY MODE" : "Signed in as " + username.trim());
        identity.getStyleClass().add(readOnly ? "badge-readonly" : "subtitle");

        statusLabel.getStyleClass().add("status-label");
        leaveButton.getStyleClass().add("leave-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, new VBox(2, title, identity), spacer, statusDot, statusLabel, leaveButton);
        header.setAlignment(Pos.CENTER_LEFT);
        add(header, 0, 0, 2, 1);

        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.getStyleClass().add("chat-area");
        GridPane.setHgrow(chatArea, Priority.ALWAYS);
        GridPane.setVgrow(chatArea, Priority.ALWAYS);
        add(chatArea, 0, 1, 2, 1);

        input.setPrefRowCount(2);
        input.setWrapText(true);
        input.setPromptText(readOnly
                ? "READ-ONLY MODE - you can read messages but not send them"
                : "Type a message (Enter to send, Shift+Enter for new line). Commands: allUsers, bye");
        GridPane.setHgrow(input, Priority.ALWAYS);
        add(input, 0, 2);

        sendButton.setMaxHeight(Double.MAX_VALUE);
        sendButton.getStyleClass().add("send-button");
        add(sendButton, 1, 2);

        if (readOnly) {
            input.setDisable(true);
            sendButton.setDisable(true);
        }

        sendButton.setOnAction(e -> sendInput());
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendInput();
            }
        });
        leaveButton.setOnAction(e -> {
            if (online && connection != null) {
                connection.disconnect();   // triggers onDisconnected()
            } else {
                this.onBackToLogin.run();
            }
        });
    }

    public void bind(ChatConnection connection) {
        this.connection = connection;
    }

    /** Called when the window is closed. */
    public void shutdown() {
        if (connection != null) connection.disconnect();
    }

    private void sendInput() {
        if (connection == null || !online) return;
        String text = input.getText();
        input.clear();
        connection.send(text);
    }

    private void appendLine(String line) {
        chatArea.appendText(line + "\n");
    }

    // ---- ChatListener (called from background threads) ---------------------------

    @Override
    public void onMessage(String formattedMessage) {
        Platform.runLater(() -> appendLine(formattedMessage));
    }

    @Override
    public void onInfo(String text) {
        Platform.runLater(() -> appendLine("*** " + text));
    }

    @Override
    public void onUserList(List<String> users) {
        Platform.runLater(() -> appendLine("*** Active users (" + users.size() + "): " + String.join(", ", users)));
    }

    @Override
    public void onError(String text) {
        Platform.runLater(() -> appendLine("!!! " + text));
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            online = false;
            statusLabel.setText("Offline");
            statusDot.setFill(OFFLINE);
            input.setDisable(true);
            sendButton.setDisable(true);
            leaveButton.setText("NEW SESSION");
            appendLine("*** Disconnected from server");
        });
    }
}

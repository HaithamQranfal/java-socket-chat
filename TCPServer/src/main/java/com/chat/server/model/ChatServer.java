package com.chat.server.model;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server model: accepts connections, one virtual thread per client, and
 * distributes messages. Contains no JavaFX code.
 */
public class ChatServer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final int port;
    private final ServerListener listener;
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    private final Map<String, ClientHandler> users = new ConcurrentHashMap<>(); // key = lower-case name
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        listener.onLog("Server Started on port " + port);
        executor.submit(this::acceptLoop);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        handlers.forEach(ClientHandler::close);
        executor.shutdownNow();
        listener.onLog("Server stopped");
    }

    private void acceptLoop() {
        while (running) {
            try {
                listener.onLog("Waiting for Client...");
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                handlers.add(handler);
                executor.submit(handler);
            } catch (IOException e) {
                if (running) listener.onLog("Accept error: " + e.getMessage());
            }
        }
    }

    // ---- called by ClientHandler -------------------------------------------------

    /** Registers a handshaken client. Returns false if the username is already taken. */
    boolean admit(ClientHandler handler) {
        if (handler.username() != null
                && users.putIfAbsent(handler.username().toLowerCase(), handler) != null) {
            return false;
        }
        handler.markAdmitted();
        if (handler.username() != null) {
            listener.onLog("Welcome " + handler.username());
            listener.onUserJoined(handler.username());
            handler.send(Protocol.INFO + " Welcome " + handler.username() + "!");
            broadcast(Protocol.INFO + " " + handler.username() + " joined the chat");
        } else {
            listener.onLog("Welcome Guest (READ-ONLY MODE)");
            handler.send(Protocol.INFO + " Connected in READ-ONLY MODE. Messages from others will appear here.");
        }
        return true;
    }

    void release(ClientHandler handler) {
        handlers.remove(handler);
        if (!handler.isAdmitted()) return;
        if (handler.username() != null) {
            users.remove(handler.username().toLowerCase(), handler);
            listener.onLog(handler.username() + " disconnected");
            listener.onUserLeft(handler.username());
            broadcast(Protocol.INFO + " " + handler.username() + " left the chat");
        } else {
            listener.onLog("Guest (read-only) disconnected");
        }
    }

    void chat(ClientHandler sender, String text) {
        String time = LocalTime.now().format(TIME);
        listener.onLog(sender.username() + ": " + text);
        broadcast(Protocol.MSG + " [" + time + "] " + sender.username() + ": " + text);
    }

    void broadcast(String line) {
        for (ClientHandler h : handlers) {
            if (h.isAdmitted()) h.send(line);
        }
    }

    List<String> userList() {
        return users.values().stream()
                .map(ClientHandler::username)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}

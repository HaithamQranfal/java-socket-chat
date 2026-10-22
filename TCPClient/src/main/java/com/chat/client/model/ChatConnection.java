package com.chat.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client model: owns the socket, the reader thread and the command handling. No JavaFX. */
public class ChatConnection {
    private final String host;
    private final int port;
    private final String username;       // empty => READ-ONLY MODE
    private final ChatListener listener;
    private final AtomicBoolean closed = new AtomicBoolean(true);

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ChatConnection(String host, int port, String username, ChatListener listener) {
        this.host = host;
        this.port = port;
        this.username = username == null ? "" : username.trim();
        this.listener = listener;
    }

    public boolean isReadOnly() { return username.isEmpty(); }
    public String getUsername() { return username; }
    public boolean isConnected() { return !closed.get(); }

    /**
     * Connects and performs the handshake. Throws if the server is unreachable or
     * rejects the username (e.g. already taken). On success a virtual thread starts
     * reading server messages.
     */
    public void connect() throws IOException {
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            out.println(isReadOnly() ? Protocol.HELLO : Protocol.HELLO + " " + username);

            // Peek at the first reply: reject on ERROR, otherwise push it back for the reader loop.
            in.mark(4096);
            socket.setSoTimeout(5000);
            String first = in.readLine();
            socket.setSoTimeout(0);
            if (first == null) throw new IOException("Server closed the connection");
            if (first.startsWith(Protocol.ERROR)) {
                throw new IOException(first.substring(Protocol.ERROR.length()).trim());
            }
            in.reset();
        } catch (IOException e) {
            closeSocket();
            throw e;
        }
        closed.set(false);
        Thread.ofVirtual().name("chat-reader").start(this::readLoop);
    }

    /**
     * Interprets user input: "end"/"bye" disconnect, "allUsers" asks for the active
     * user list, anything else is a chat message (not allowed in read-only mode).
     */
    public boolean send(String text) {
        if (closed.get() || text == null) return false;
        String t = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (t.isEmpty()) return false;

        String lower = t.toLowerCase();
        if (lower.equals("end") || lower.equals("bye")) {
            disconnect();
            return true;
        }
        if (lower.equals("allusers")) {
            out.println(Protocol.ALLUSERS);
            return true;
        }
        if (isReadOnly()) {
            listener.onError("READ-ONLY MODE: enter a username to send messages.");
            return false;
        }
        out.println(Protocol.MSG + " " + t);
        return true;
    }

    /** Tells the server we are leaving, then closes the connection. */
    public void disconnect() {
        if (closed.get()) return;
        out.println(Protocol.BYE);
        shutdown();
    }

    private void readLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            // socket closed locally or connection lost
        } finally {
            shutdown();
        }
    }

    private void dispatch(String line) {
        if (line.startsWith(Protocol.MSG + " ")) {
            listener.onMessage(line.substring(Protocol.MSG.length() + 1));
        } else if (line.startsWith(Protocol.INFO)) {
            listener.onInfo(line.substring(Protocol.INFO.length()).trim());
        } else if (line.startsWith(Protocol.USERS)) {
            String payload = line.substring(Protocol.USERS.length()).trim();
            List<String> users = payload.isEmpty() ? List.of() : Arrays.asList(payload.split(","));
            listener.onUserList(users);
        } else if (line.startsWith(Protocol.ERROR)) {
            listener.onError(line.substring(Protocol.ERROR.length()).trim());
        }
    }

    private void shutdown() {
        if (closed.compareAndSet(false, true)) {
            closeSocket();
            listener.onDisconnected();
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}

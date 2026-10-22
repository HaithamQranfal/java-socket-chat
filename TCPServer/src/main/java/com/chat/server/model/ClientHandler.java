package com.chat.server.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Serves a single client connection on its own virtual thread. */
class ClientHandler implements Runnable {
    private static final int MAX_NAME = 20;

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private volatile String username;   // null => read-only guest
    private volatile boolean admitted;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    String username() { return username; }
    boolean isAdmitted() { return admitted; }
    void markAdmitted() { admitted = true; }

    void send(String line) {
        PrintWriter w = out;
        if (w != null) w.println(line); // println is synchronized and auto-flushes
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            if (!handshake(in)) return;

            String line;
            while ((line = in.readLine()) != null) {
                if (!process(line)) break;
            }
        } catch (IOException e) {
            // connection dropped; cleanup below
        } finally {
            server.release(this);
            close();
        }
    }

    private boolean handshake(BufferedReader in) throws IOException {
        String first = in.readLine();
        if (first == null || !(first.equals(Protocol.HELLO) || first.startsWith(Protocol.HELLO + " "))) {
            send(Protocol.ERROR + " Expected HELLO");
            return false;
        }
        String name = first.substring(Protocol.HELLO.length()).trim();
        if (!name.isEmpty()) {
            if (name.length() > MAX_NAME || name.contains(",") || name.chars().anyMatch(Character::isISOControl)) {
                send(Protocol.ERROR + " Invalid username (max " + MAX_NAME + " chars, no commas)");
                return false;
            }
            username = name;
        }
        if (!server.admit(this)) {
            send(Protocol.ERROR + " Username already taken");
            return false;
        }
        return true;
    }

    /** @return false when the client asked to leave. */
    private boolean process(String line) {
        if (line.equals(Protocol.BYE)) return false;
        if (line.equals(Protocol.ALLUSERS)) {
            send(Protocol.USERS + " " + String.join(",", server.userList()));
        } else if (line.startsWith(Protocol.MSG + " ")) {
            if (username == null) {
                send(Protocol.ERROR + " READ-ONLY MODE: you cannot send messages.");
            } else {
                String text = line.substring(Protocol.MSG.length() + 1).trim();
                if (!text.isEmpty()) server.chat(this, text);
            }
        }
        return true;
    }
}

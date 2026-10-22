# Multi-Client Chat Application (Java Sockets + JavaFX)

A TCP group-chat system made of two independent Maven projects:

| Project | Role |
|---|---|
| `TCPServer/` | Central server. Accepts many clients, formats and broadcasts messages, shows a live user list and an activity log. |
| `TCPClient/` | JavaFX chat client with login, read-only mode, `allUsers`, and clean disconnect. |

**Stack:** Java 21 (virtual threads), Java TCP sockets, JavaFX 21 (`GridPane` + CSS), Maven, IntelliJ IDEA.

## Architecture

Both projects separate **model** (sockets, protocol, threads; no JavaFX imports) from **view** (JavaFX).

```
TCPServer                              TCPClient
  view:  ServerApp                       view:  ClientApp, ChatView
  model: ChatServer, ClientHandler,      model: ChatConnection, ChatListener,
         ServerListener, Protocol,              Protocol, AppConfig
         AppConfig
```

The model talks to the UI only through a listener interface (`ServerListener` / `ChatListener`).
The views implement it and wrap every UI update in `Platform.runLater(...)`. The model can be driven
by a console app or a test with no UI at all.

**Concurrency:** `ChatServer` uses `Executors.newVirtualThreadPerTaskExecutor()`: one virtual thread
accepts connections and one virtual thread serves each client. The client reads from the socket on a
virtual thread.

### Wire protocol (line-based, UTF-8)

| Direction | Line | Meaning |
|---|---|---|
| C -> S | `HELLO <username>` / `HELLO` | Handshake. Empty name = READ-ONLY MODE. |
| C -> S | `MSG <text>` | Chat message |
| C -> S | `ALLUSERS` | Ask for active users |
| C -> S | `BYE` | Leave |
| S -> C | `MSG [HH:mm] user: text` | Broadcast chat message |
| S -> C | `INFO <text>` | Welcome / joined / left notices |
| S -> C | `USERS a,b,c` | Reply to `ALLUSERS` (requester only) |
| S -> C | `ERROR <text>` | Rejected username, read-only violation, ... |

Read-only mode is enforced on **both** sides: the client disables input, and the server rejects `MSG`
from a nameless connection.

## Requirements -> implementation

| Requirement | Where |
|---|---|
| Username before chat; read-only if empty | `ClientApp` login screen, `ChatView`, `ClientHandler.process` |
| Send via button or Enter | `ChatView` (Shift+Enter = newline) |
| `allUsers` | `ChatConnection.send` -> `ClientHandler` -> `ChatServer.userList` |
| `end` / `bye` / LEAVE | `ChatConnection.send` / `disconnect` |
| Online label + circle | `ChatView` (turns grey and "Offline" on disconnect) |
| Concurrent clients, virtual threads | `ChatServer` |
| Message formatted with user and time | `ChatServer.chat` |
| Live `ListView` of users, random colours | `ServerApp` (`CellFactory`) |
| Activity log | `ServerListener.onLog` -> log `TextArea` |
| Config file for IP/port | `config.properties` next to the JAR |
| CLI arguments | `java TCPClient <ip> <port>`, `java TCPServer` |

Extra behaviour: usernames are unique (case-insensitive), max 20 chars, no commas.

## Configuration

`config.properties` is read from the **working directory** at runtime (not from inside the JAR), so
you can change it without recompiling.

* Server: `server.port=3000`
* Client: `server.host=localhost`, `server.port=3000`. Command-line arguments override these values.

## Build

Requires **JDK 21+** and **Maven 3.9+**.

```bash
cd TCPServer && mvn clean package     # -> target/TCPServer.jar + target/config.properties
cd ../TCPClient && mvn clean package  # -> target/TCPClient.jar + target/config.properties
```

> The shaded JAR bundles the JavaFX natives of the OS you build on. Build on the OS where you will run it
> (or build once per OS).

## Run

```bash
# 1. server (from TCPServer/target)
java -jar TCPServer.jar

# 2. clients (from TCPClient/target), as many as you like
java -jar TCPClient.jar localhost 3000
java -jar TCPClient.jar            # uses config.properties
```

From the IDE or CLI without packaging: `mvn javafx:run` (client: `mvn javafx:run -Djavafx.args="localhost 3000"`).

## Demo script (3 minutes)

1. Show the package structure (model vs view) and the `ChatListener` interface (30 s).
2. Start the server: log shows "Server Started" and "Waiting for Client...".
3. Start two clients with usernames. The server list shows coloured entries and "Welcome ...".
4. Chat between them, then type `allUsers`.
5. Start a third client with an empty username to show READ-ONLY MODE.
6. Try a duplicate username to show rejection.
7. Disconnect with `bye`, then with LEAVE. Show the server list and log updating.

## UML

PlantUML sources are in `docs/`: `class-diagram.puml`, `deployment-diagram.puml`, and the optional
`sequence-diagram.puml` and `usecase-diagram.puml`. Render with the PlantUML IntelliJ plugin or
<https://www.plantuml.com/plantuml>, then export PNG/PDF for the submission.

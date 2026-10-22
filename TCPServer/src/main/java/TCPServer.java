import com.chat.server.view.ServerApp;
import javafx.application.Application;

/**
 * Launcher. Deliberately does not extend {@link Application} so the shaded JAR
 * starts without the JavaFX module path.
 * Usage: java TCPServer   (port comes from config.properties)
 */
public class TCPServer {
    public static void main(String[] args) {
        Application.launch(ServerApp.class, args);
    }
}

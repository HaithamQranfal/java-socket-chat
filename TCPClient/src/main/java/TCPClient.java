import com.chat.client.view.ClientApp;
import javafx.application.Application;

/**
 * Launcher. Deliberately does not extend {@link Application} so the shaded JAR
 * starts without the JavaFX module path.
 * Usage: java TCPClient <ServerIPAddress> <PortNumber>   e.g. java TCPClient localhost 3000
 * With no arguments, host and port come from config.properties.
 */
public class TCPClient {
    public static void main(String[] args) {
        if (args.length != 0 && args.length != 2) {
            System.err.println("Usage: java TCPClient <ServerIPAddress> <PortNumber>");
            System.exit(1);
        }
        Application.launch(ClientApp.class, args);
    }
}

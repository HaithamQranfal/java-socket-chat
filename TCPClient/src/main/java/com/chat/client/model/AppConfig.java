package com.chat.client.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads settings from a .properties file in the working directory. */
public final class AppConfig {
    private AppConfig() {}

    public static Properties load(String fileName) {
        Properties props = new Properties();
        Path path = Path.of(fileName);
        if (Files.isReadable(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("Could not read " + path.toAbsolutePath() + ": " + e.getMessage());
            }
        } else {
            System.err.println(path.toAbsolutePath() + " not found, using defaults.");
        }
        return props;
    }

    public static int getInt(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

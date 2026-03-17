package com.tomtom.orbis.comparator.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads configuration from a local properties file.
 *
 * Resolution order (first found wins):
 *   1. ./.mcr-compare.properties  (project/working directory)
 *   2. ~/.mcr-compare.properties  (user home directory)
 *
 * Supported keys:
 *   databricks.token       — Databricks Personal Access Token
 *   databricks.host        — Databricks server hostname
 *   databricks.http_path   — SQL warehouse HTTP path
 *   databricks.jdbc_url    — Full JDBC URL (overrides host/http_path/token)
 */
public class AppConfig {
    private static final String CONFIG_FILENAME = ".mcr-compare.properties";
    private final Properties props = new Properties();
    private final Path loadedFrom;

    private AppConfig(Path loadedFrom) {
        this.loadedFrom = loadedFrom;
    }

    /** Load config from the first properties file found in the resolution order. */
    public static AppConfig load() {
        Path[] candidates = {
                Path.of(CONFIG_FILENAME),
                Path.of(System.getProperty("user.home"), CONFIG_FILENAME)
        };

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try (InputStream in = Files.newInputStream(candidate)) {
                    AppConfig config = new AppConfig(candidate);
                    config.props.load(in);
                    return config;
                } catch (IOException e) {
                    System.err.println("WARNING: Failed to read " + candidate + ": " + e.getMessage());
                }
            }
        }
        return new AppConfig(null);
    }

    /** Load config from a specific file. */
    public static AppConfig loadFrom(Path path) throws IOException {
        AppConfig config = new AppConfig(path);
        try (InputStream in = Files.newInputStream(path)) {
            config.props.load(in);
        }
        return config;
    }

    public String getToken() { return get("databricks.token"); }
    public String getHost() { return get("databricks.host"); }
    public String getHttpPath() { return get("databricks.http_path"); }
    public String getJdbcUrl() { return get("databricks.jdbc_url"); }

    public Path getLoadedFrom() { return loadedFrom; }
    public boolean isLoaded() { return loadedFrom != null; }

    private String get(String key) {
        String val = props.getProperty(key);
        return (val != null && !val.isBlank()) ? val.trim() : null;
    }
}

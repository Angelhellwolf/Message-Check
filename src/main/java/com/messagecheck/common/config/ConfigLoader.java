package com.messagecheck.common.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigLoader {
    private static final Yaml YAML = new Yaml();

    private ConfigLoader() {
    }

    public static MessageCheckConfig load(Path path, Logger logger) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Configuration file not found: " + path);
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            Map<String, Object> data = YAML.load(inputStream);
            if (data == null) {
                throw new IOException("Configuration file is empty: " + path);
            }
            return MessageCheckConfig.from(data);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to load configuration", ex);
            throw new IOException("Failed to load configuration: " + ex.getMessage(), ex);
        }
    }
}

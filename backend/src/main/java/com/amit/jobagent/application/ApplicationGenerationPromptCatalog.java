package com.amit.jobagent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class ApplicationGenerationPromptCatalog {
    final String planSystem;
    final String writeSystem;
    final String planSchema;
    final String writeSchema;
    final JsonNode planSchemaDocument;
    final JsonNode writeSchemaDocument;
    final String promptChecksum;
    final String schemaChecksum;

    ApplicationGenerationPromptCatalog(ContentGenerationProperties config, ObjectMapper mapper) {
        String promptBase = "prompts/application-generation/" + safeVersion(config.promptVersion()) + "/";
        String schemaBase = "prompts/application-generation/" + safeVersion(config.schemaVersion()) + "/";
        planSystem = read(promptBase + "plan-system.md");
        writeSystem = read(promptBase + "write-system.md");
        planSchema = read(schemaBase + "plan-schema.json");
        writeSchema = read(schemaBase + "write-schema.json");
        planSchemaDocument = parseSchema(mapper, schemaBase + "plan-schema.json", planSchema);
        writeSchemaDocument = parseSchema(mapper, schemaBase + "write-schema.json", writeSchema);
        promptChecksum = hash(planSystem + "\n" + writeSystem);
        schemaChecksum = hash(planSchema + "\n" + writeSchema);
    }

    private static String safeVersion(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,40}")) {
            throw new IllegalStateException("Application generation version is invalid");
        }
        return value;
    }

    private static String read(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Required application-generation resource is missing: " + path, exception);
        }
    }

    private static JsonNode parseSchema(ObjectMapper mapper, String path, String source) {
        try {
            JsonNode schema = mapper.readTree(source);
            if (schema == null || !schema.isObject()) {
                throw new IllegalStateException("Application-generation schema must be a JSON object: " + path);
            }
            return schema;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Application-generation schema is invalid JSON: " + path, exception);
        }
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}

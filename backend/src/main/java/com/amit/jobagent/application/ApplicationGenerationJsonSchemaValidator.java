package com.amit.jobagent.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Fail-closed validation for the JSON Schema subset used by the checked-in
 * application-generation contracts. The same parsed schema is sent to OpenAI.
 */
final class ApplicationGenerationJsonSchemaValidator {
    private ApplicationGenerationJsonSchemaValidator() {}

    static void validate(JsonNode schema, JsonNode value) {
        validate("$", schema, value);
    }

    private static void validate(String path, JsonNode schema, JsonNode value) {
        JsonNode type = schema.get("type");
        if (type != null && !matchesType(type, value)) {
            violation(path, "type");
        }

        JsonNode allowed = schema.get("enum");
        if (allowed != null) {
            if (!allowed.isArray()) violation(path, "schema enum");
            boolean matches = false;
            for (JsonNode candidate : allowed) {
                if (candidate.equals(value)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) violation(path, "enum");
        }

        if (value.isObject()) validateObject(path, schema, value);
        if (value.isArray()) validateArray(path, schema, value);
        if (value.isTextual()) validateString(path, schema, value.textValue());
        if (value.isNumber()) validateNumber(path, schema, value);
    }

    private static void validateObject(String path, JsonNode schema, JsonNode value) {
        JsonNode properties = schema.get("properties");
        if (properties != null && !properties.isObject()) violation(path, "schema properties");

        JsonNode required = schema.get("required");
        if (required != null) {
            if (!required.isArray()) violation(path, "schema required");
            for (JsonNode field : required) {
                if (!field.isTextual() || !value.has(field.textValue())) {
                    violation(path, "required property");
                }
            }
        }

        if (schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").booleanValue()) {
            Set<String> known = new HashSet<>();
            if (properties != null) properties.fieldNames().forEachRemaining(known::add);
            value.fieldNames().forEachRemaining(field -> {
                if (!known.contains(field)) violation(path + "." + field, "additional property");
            });
        }

        if (properties != null) {
            properties.fields().forEachRemaining(property -> {
                JsonNode child = value.get(property.getKey());
                if (child != null) validate(path + "." + property.getKey(), property.getValue(), child);
            });
        }
    }

    private static void validateArray(String path, JsonNode schema, JsonNode value) {
        JsonNode minItems = schema.get("minItems");
        JsonNode maxItems = schema.get("maxItems");
        if (minItems != null && (!minItems.canConvertToInt() || value.size() < minItems.intValue())) {
            violation(path, "minItems");
        }
        if (maxItems != null && (!maxItems.canConvertToInt() || value.size() > maxItems.intValue())) {
            violation(path, "maxItems");
        }
        JsonNode items = schema.get("items");
        if (items != null) {
            for (int index = 0; index < value.size(); index++) {
                validate(path + "[" + index + "]", items, value.get(index));
            }
        }
    }

    private static void validateString(String path, JsonNode schema, String value) {
        JsonNode minLength = schema.get("minLength");
        JsonNode maxLength = schema.get("maxLength");
        if (minLength != null && (!minLength.canConvertToInt() || value.length() < minLength.intValue())) {
            violation(path, "minLength");
        }
        if (maxLength != null && (!maxLength.canConvertToInt() || value.length() > maxLength.intValue())) {
            violation(path, "maxLength");
        }
        JsonNode pattern = schema.get("pattern");
        if (pattern != null) {
            if (!pattern.isTextual()) violation(path, "schema pattern");
            try {
                if (!Pattern.compile(pattern.textValue()).matcher(value).find()) violation(path, "pattern");
            } catch (PatternSyntaxException exception) {
                throw new IllegalStateException("Invalid application-generation schema pattern", exception);
            }
        }
    }

    private static void validateNumber(String path, JsonNode schema, JsonNode value) {
        JsonNode minimum = schema.get("minimum");
        JsonNode maximum = schema.get("maximum");
        if (minimum != null && (!minimum.isNumber() || value.decimalValue().compareTo(minimum.decimalValue()) < 0)) {
            violation(path, "minimum");
        }
        if (maximum != null && (!maximum.isNumber() || value.decimalValue().compareTo(maximum.decimalValue()) > 0)) {
            violation(path, "maximum");
        }
    }

    private static boolean matchesType(JsonNode type, JsonNode value) {
        if (type.isTextual()) return matchesType(type.textValue(), value);
        if (!type.isArray()) violation("$", "schema type");
        for (JsonNode candidate : type) {
            if (!candidate.isTextual()) violation("$", "schema type");
            if (matchesType(candidate.textValue(), value)) return true;
        }
        return false;
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> throw new IllegalStateException("Unsupported application-generation schema type: " + type);
        };
    }

    private static void violation(String path, String keyword) {
        throw new IllegalStateException("CONTENT_GENERATION_SCHEMA_VIOLATION at " + path + " (" + keyword + ")");
    }
}

package com.ruoyi.system.service.cost.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

/**
 * Applies third-party authentication configuration to remote requests.
 */
@Service
public class RemoteAuthHandler {
    public void appendAuthQueryParams(UriComponentsBuilder builder, RemoteVariableConfig config) {
        if (!"API_KEY".equals(config.authType) || config.authConfig == null || config.authConfig.isNull()) {
            return;
        }
        String location = firstNonBlank(textValue(config.authConfig, "location"), "HEADER");
        if (!"QUERY".equalsIgnoreCase(location)) {
            return;
        }
        String keyName = firstNonBlank(textValue(config.authConfig, "keyName"), textValue(config.authConfig, "queryName"));
        String keyValue = textValue(config.authConfig, "keyValue");
        if (StringUtils.isNotEmpty(keyName) && StringUtils.isNotEmpty(keyValue)) {
            builder.replaceQueryParam(keyName, keyValue);
        }
    }

    public void applyAuthHeaders(HttpHeaders headers, RemoteVariableConfig config) {
        JsonNode authConfig = config.authConfig;
        String authType = StringUtils.defaultIfEmpty(config.authType, "NONE");
        if ("NONE".equals(authType) || authConfig == null || authConfig.isNull()) {
            return;
        }
        switch (authType) {
            case "BASIC" -> {
                String username = textValue(authConfig, "username");
                String password = textValue(authConfig, "password");
                if (StringUtils.isNotEmpty(username)) {
                    headers.setBasicAuth(StringUtils.defaultString(username), StringUtils.defaultString(password), StandardCharsets.UTF_8);
                }
            }
            case "BEARER" -> {
                String headerName = firstNonBlank(textValue(authConfig, "headerName"), HttpHeaders.AUTHORIZATION);
                String prefix = normalizeBearerPrefix(firstNonBlank(textValue(authConfig, "prefix"), "Bearer"));
                String token = firstNonBlank(textValue(authConfig, "token"), textValue(authConfig, "accessToken"));
                if (StringUtils.isNotEmpty(token)) {
                    headers.set(headerName, prefix + token);
                }
            }
            case "API_KEY" -> {
                String location = firstNonBlank(textValue(authConfig, "location"), "HEADER");
                String keyName = firstNonBlank(textValue(authConfig, "keyName"), textValue(authConfig, "headerName"));
                String keyValue = textValue(authConfig, "keyValue");
                if ("HEADER".equalsIgnoreCase(location) && StringUtils.isNotEmpty(keyName) && StringUtils.isNotEmpty(keyValue)) {
                    headers.set(keyName, keyValue);
                }
            }
            case "COOKIE" -> {
                String rawCookie = firstNonBlank(textValue(authConfig, "rawCookie"), textValue(authConfig, "cookie"));
                if (StringUtils.isNotEmpty(rawCookie)) {
                    headers.set(HttpHeaders.COOKIE, rawCookie);
                } else {
                    String cookieName = textValue(authConfig, "cookieName");
                    String cookieValue = textValue(authConfig, "cookieValue");
                    if (StringUtils.isNotEmpty(cookieName) && StringUtils.isNotEmpty(cookieValue)) {
                        headers.set(HttpHeaders.COOKIE, cookieName + "=" + cookieValue);
                    }
                }
            }
            default -> {
            }
        }
    }

    public String resolveAuthHeaderName(RemoteVariableConfig config) {
        if (config.authConfig == null || config.authConfig.isNull()) {
            return "";
        }
        if ("BEARER".equals(config.authType)) {
            return firstNonBlank(textValue(config.authConfig, "headerName"), HttpHeaders.AUTHORIZATION);
        }
        if (!"API_KEY".equals(config.authType)) {
            return "";
        }
        String location = firstNonBlank(textValue(config.authConfig, "location"), "HEADER");
        if (!"HEADER".equalsIgnoreCase(location)) {
            return "";
        }
        return firstNonBlank(textValue(config.authConfig, "keyName"), textValue(config.authConfig, "headerName"));
    }

    public boolean hasConfiguredAuthToken(RemoteVariableConfig config) {
        if (config.authConfig == null || config.authConfig.isNull()) {
            return false;
        }
        return switch (config.authType) {
            case "BEARER" ->
                    StringUtils.isNotEmpty(firstNonBlank(textValue(config.authConfig, "token"), textValue(config.authConfig, "accessToken")));
            case "BASIC" -> StringUtils.isNotEmpty(textValue(config.authConfig, "username"));
            case "API_KEY" -> StringUtils.isNotEmpty(textValue(config.authConfig, "keyValue"));
            case "COOKIE" ->
                    StringUtils.isNotEmpty(firstNonBlank(textValue(config.authConfig, "rawCookie"), textValue(config.authConfig, "cookie")))
                            || (StringUtils.isNotEmpty(textValue(config.authConfig, "cookieName"))
                            && StringUtils.isNotEmpty(textValue(config.authConfig, "cookieValue")));
            default -> false;
        };
    }

    private String textValue(JsonNode objectNode, String fieldName) {
        if (objectNode == null || objectNode.isNull() || StringUtils.isEmpty(fieldName)) {
            return "";
        }
        JsonNode node = objectNode.get(fieldName);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotEmpty(StringUtils.trim(first)) ? StringUtils.trim(first) : StringUtils.trimToEmpty(second);
    }

    private String normalizeBearerPrefix(String prefix) {
        String normalized = StringUtils.trimToEmpty(prefix);
        if (StringUtils.isEmpty(normalized)) {
            return "";
        }
        return normalized.endsWith(" ") ? normalized : normalized + " ";
    }
}

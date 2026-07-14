package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class OAuthAttributes {
    private Map<String, Object> attributes;

    private String name;
    private String email;
    private String picture;
    private String nameAttributeKey;
    private boolean emailVerified;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey, String name, String email,
            String picture, boolean emailVerified) {
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.emailVerified = emailVerified;
    }

    public String getProviderId() {
        Object providerId = attributes.get(nameAttributeKey);
        if (providerId == null || String.valueOf(providerId).isBlank()) {
            throw invalidUserInfo("OAuth2 provider id is missing.");
        }
        return String.valueOf(providerId);
    }

    public static OAuthAttributes of(String registrationId, String userNameAttributeName,
            Map<String, Object> attributes) {
        if (attributes == null) {
            throw invalidUserInfo("OAuth2 user attributes are missing.");
        }

        return switch (registrationId) {
            case "google" -> ofGoogle(userNameAttributeName, attributes);
            case "naver" -> ofNaver("id", attributes);
            case "github" -> ofGithub(userNameAttributeName, attributes);
            case "discord" -> ofDiscord(userNameAttributeName, attributes);
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "Unsupported OAuth2 provider.");
        };
    }

    private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .name(optionalString(attributes.get("name")))
                .email(optionalString(attributes.get("email")))
                .picture(optionalString(attributes.get("picture")))
                .emailVerified(isTrue(attributes.get("email_verified")))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuthAttributes ofNaver(String userNameAttributeName, Map<String, Object> attributes) {
        Map<String, Object> response = stringKeyedMap(attributes.get("response"));

        return OAuthAttributes.builder()
                .name(optionalString(response.get("name")))
                .email(optionalString(response.get("email")))
                .picture(optionalString(response.get("profile_image")))
                .emailVerified(false)
                .attributes(response)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuthAttributes ofGithub(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .name(optionalString(attributes.get("name")))
                .email(optionalString(attributes.get("email")))
                .picture(optionalString(attributes.get("avatar_url")))
                .emailVerified(false)
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuthAttributes ofDiscord(String userNameAttributeName, Map<String, Object> attributes) {
        String providerId = requiredString(attributes.get("id"), "OAuth2 provider id is missing.");
        String avatar = optionalString(attributes.get("avatar"));
        return OAuthAttributes.builder()
                .name(optionalString(attributes.get("username")))
                .email(optionalString(attributes.get("email")))
                .picture(avatar == null ? null
                        : "https://cdn.discordapp.com/avatars/" + providerId + "/" + avatar + ".png")
                .emailVerified(isTrue(attributes.get("verified")))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static Map<String, Object> stringKeyedMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw invalidUserInfo("OAuth2 nested user attributes are invalid.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalidUserInfo("OAuth2 nested user attributes contain an invalid key.");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static String optionalString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private static String requiredString(Object value, String message) {
        String stringValue = value == null ? null : String.valueOf(value);
        if (stringValue == null || stringValue.isBlank()) {
            throw invalidUserInfo(message);
        }
        return stringValue;
    }

    private static OAuth2AuthenticationException invalidUserInfo(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"), message);
    }

    public User toEntity(String registrationId, String nameAttributeKey) {
        String providerId = String.valueOf(attributes.get(nameAttributeKey));
        return User.builder()
                .loginId(registrationId + "_" + providerId)
                .displayName(name)
                .email(email)
                .password(UUID.randomUUID().toString())
                .build();
    }
}

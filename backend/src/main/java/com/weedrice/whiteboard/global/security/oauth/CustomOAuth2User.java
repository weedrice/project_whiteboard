package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomOAuth2User extends CustomUserDetails implements OAuth2User {

    private final Map<String, Object> attributes;
    private final String nameAttributeKey;

    public CustomOAuth2User(User user, Map<String, Object> attributes, String nameAttributeKey,
            Collection<? extends GrantedAuthority> authorities) {
        super(user.getUserId(), user.getLoginId(), user.getPassword(), authorities);
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return (String) attributes.get(nameAttributeKey);
    }
}

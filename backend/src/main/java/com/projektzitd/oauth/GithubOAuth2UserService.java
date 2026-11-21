package com.projektzitd.oauth;

import com.projektzitd.user.User;
import com.projektzitd.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub login (OAuth 2.0). Spring handles the redirect, code exchange and
 * fetching /user; this class maps the GitHub user to a local account and
 * exposes the LOCAL username as the principal name.
 */

@Service
@RequiredArgsConstructor
public class GithubOAuth2UserService extends DefaultOAuth2UserService {

    private static final String LOCAL_USERNAME = "localUsername";

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User githubUser = super.loadUser(userRequest);

        Object id = githubUser.getAttribute("id");
        String login = githubUser.getAttribute("login");
        if (!(id instanceof Number) || login == null || login.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"), "GitHub did not return id/login");
        }

        User user = userService.findOrCreateGithubUser(((Number) id).longValue(), login);

        Map<String, Object> attributes = new HashMap<>(githubUser.getAttributes());
        attributes.put(LOCAL_USERNAME, user.getUsername());

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority(user.getRole())),
                attributes,
                LOCAL_USERNAME);
    }
}

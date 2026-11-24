package com.projektzitd.oauth;

import com.projektzitd.user.User;
import com.projektzitd.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Google login (OpenID Connect). Maps the Google account to a local User
 * and exposes the LOCAL username as the principal name.
 */
@Service
@RequiredArgsConstructor
public class GoogleOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser googleUser = super.loadUser(userRequest);

        String sub = googleUser.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"), "Google did not return sub");
        }

        User user = userService.findOrCreateGoogleUser(sub, googleUser.getEmail());

        return new LocalOidcUser(
                List.of(new SimpleGrantedAuthority(user.getRole())),
                googleUser.getIdToken(),
                googleUser.getUserInfo(),
                user.getUsername());
    }

    /** OIDC principal whose getName() is the local username. */
    private static final class LocalOidcUser extends DefaultOidcUser {
        private final String localUsername;

        LocalOidcUser(Collection<? extends GrantedAuthority> authorities,
                      OidcIdToken idToken, OidcUserInfo userInfo, String localUsername) {
            super(authorities, idToken, userInfo);
            this.localUsername = localUsername;
        }

        @Override
        public String getName() {
            return localUsername;
        }
    }
}
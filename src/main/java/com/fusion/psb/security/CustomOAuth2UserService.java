package com.fusion.psb.security;

import com.fusion.psb.entity.Role;
import com.fusion.psb.entity.User;
import com.fusion.psb.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email    = oAuth2User.getAttribute("email");
        String name     = oAuth2User.getAttribute("name");
        String picture  = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        userRepository.findByEmail(email).ifPresentOrElse(
            existing -> {
                existing.setName(name);
                existing.setPicture(picture);
                existing.setLastLogin(LocalDateTime.now());
                if("abhiit61@gmail.com".equalsIgnoreCase(email)) {
                    existing.setRole(Role.ADMIN);
                }
                userRepository.save(existing);
            },
            () -> {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setName(name);
                newUser.setPicture(picture);
                newUser.setGoogleId(googleId);
                newUser.setRole(Role.USER);
                newUser.setCreatedAt(LocalDateTime.now());
                newUser.setLastLogin(LocalDateTime.now());
                if("abhiit61@gmail.com".equalsIgnoreCase(email)) {
                    newUser.setRole(Role.ADMIN);
                }
                userRepository.save(newUser);
            }
        );

        return oAuth2User;
    }
}

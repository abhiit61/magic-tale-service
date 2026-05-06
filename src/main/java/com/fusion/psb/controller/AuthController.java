package com.fusion.psb.controller;

import com.fusion.psb.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        return ResponseEntity.ok(Map.of(
            "id",        user.getId(),
            "email",     user.getEmail(),
            "name",      user.getName(),
            "picture",   user.getPicture() != null ? user.getPicture() : "",
            "role",      user.getRole().name(),
            "createdAt", user.getCreatedAt().toString(),
            "lastLogin", user.getLastLogin().toString()
        ));
    }
}

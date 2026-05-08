package com.fusion.psb.controller;

import com.fusion.psb.entity.User;
import com.fusion.psb.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<Map<String, Object>> users = userService.getAllUsers()
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(userService.getUserById(id)));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateUser(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(userService.deactivateUser(id)));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(toResponse(userService.updateRole(id, body.get("role"))));
    }

    private Map<String, Object> toResponse(User user) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        return Map.of(
                "id",         user.getId(),
                "name",       user.getName() != null ? user.getName() : "",
                "email",      user.getEmail(),
                "role",       user.getRole().name(),
                "active",     user.isActive(),
                "createdAt",  user.getCreatedAt() != null ? user.getCreatedAt().format(fmt) + "Z" : "",
                "pictureUrl", user.getPicture() != null ? user.getPicture() : ""
        );
    }
}

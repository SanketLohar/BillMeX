package com.billme.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/profile-photo")
    public ResponseEntity<?> uploadProfilePhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        UserResponse response = userService.uploadProfilePhoto(email, file);
        return ResponseEntity.ok(java.util.Map.of("imageUrl", response.getProfileImageUrl()));
    }
}

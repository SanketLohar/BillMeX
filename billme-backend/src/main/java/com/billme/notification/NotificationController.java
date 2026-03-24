package com.billme.notification;

import com.billme.notification.dto.NotificationResponse;
import com.billme.user.User;
import com.billme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.ok(notificationService.getUserNotifications(user));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        notificationService.markAsRead(id, user);
        return ResponseEntity.ok().build();
    }
}

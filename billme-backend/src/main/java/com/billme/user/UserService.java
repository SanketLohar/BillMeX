package com.billme.user;

import com.billme.repository.UserRepository;
import com.billme.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public UserResponse uploadProfilePhoto(String email, MultipartFile file) throws IOException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        // Limit size to 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size must be less than 5MB");
        }

        // Delete old photo if exists
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            String oldFileName = user.getProfileImageUrl().substring(user.getProfileImageUrl().lastIndexOf('/') + 1);
            try {
                fileStorageService.deleteFile(oldFileName, "profiles");
            } catch (Exception e) {
                // Log and continue
            }
        }

        // Store new photo
        String fileName = fileStorageService.storeFile(file, "profiles");
        String fileUrl = fileStorageService.getFileUrl(fileName, "profiles");

        user.setProfileImageUrl(fileUrl);
        userRepository.save(user);

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .active(user.isActive())
                .build();
    }
    
    public UserResponse getUserDetails(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .active(user.isActive())
                .build();
    }
}

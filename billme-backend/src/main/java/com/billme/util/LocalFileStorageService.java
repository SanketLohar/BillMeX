package com.billme.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path rootLocation;

    @Value("${app.upload.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    public LocalFileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = "";
        
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) {
            extension = originalFileName.substring(i);
        }

        String fileName = UUID.randomUUID().toString() + extension;
        Path targetLocation = rootLocation.resolve(subDirectory);
        Files.createDirectories(targetLocation);
        
        Files.copy(file.getInputStream(), targetLocation.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        
        return fileName;
    }

    @Override
    public void deleteFile(String fileName, String subDirectory) throws IOException {
        Path filePath = rootLocation.resolve(subDirectory).resolve(fileName);
        Files.deleteIfExists(filePath);
    }

    @Override
    public String getFileUrl(String fileName, String subDirectory) {
        if (fileName == null || fileName.isEmpty()) return null;
        return baseUrl + "/" + subDirectory + "/" + fileName;
    }
}

package com.billme.util;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * Cloud-ready abstraction for file storage.
 */
public interface FileStorageService {
    String storeFile(MultipartFile file, String subDirectory) throws IOException;
    void deleteFile(String fileName, String subDirectory) throws IOException;
    String getFileUrl(String fileName, String subDirectory);
}

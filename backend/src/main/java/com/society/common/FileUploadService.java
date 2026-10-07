package com.society.common;

import com.society.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    public String uploadFile(MultipartFile file, String subFolder) {
        if (file.isEmpty()) {
            throw new BusinessException("File is empty");
        }

        try {
            Path uploadPath = Paths.get(uploadDir, subFolder);
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newFilename = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            Path filePath = uploadPath.resolve(newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return subFolder + "/" + newFilename;
        } catch (IOException e) {
            // Log the full cause/stack trace so filesystem issues (e.g. permission
            // denied on a mounted volume) are visible in the backend logs instead of
            // being swallowed by the BusinessException -> 400 handler.
            log.error("Failed to upload file to subFolder '{}' under uploadDir '{}': {}",
                    subFolder, uploadDir, e.toString(), e);
            throw new BusinessException("Failed to upload file: " + e.getMessage());
        }
    }
}

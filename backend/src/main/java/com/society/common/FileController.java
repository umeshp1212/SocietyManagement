package com.society.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/files")
public class FileController {

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * Serve uploaded file for viewing (inline) or downloading.
     * URL: /api/files/view/{folder}/{subfolder}/{filename}
     * Example: /api/files/view/vouchers/5/20260810_abc123.pdf
     */
    @GetMapping("/view/**")
    public ResponseEntity<Resource> viewFile(@RequestParam(required = false) String path,
                                              jakarta.servlet.http.HttpServletRequest request) {
        try {
            // Extract the file path from the URL after /files/view/
            String fullPath = request.getRequestURI();
            String filePath = fullPath.substring(fullPath.indexOf("/files/view/") + "/files/view/".length());

            Path file = Paths.get(uploadDir).resolve(filePath).normalize();

            if (!Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(file.toUri());
            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download uploaded file as attachment.
     * URL: /api/files/download/{folder}/{subfolder}/{filename}
     */
    @GetMapping("/download/**")
    public ResponseEntity<Resource> downloadFile(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String fullPath = request.getRequestURI();
            String filePath = fullPath.substring(fullPath.indexOf("/files/download/") + "/files/download/".length());

            Path file = Paths.get(uploadDir).resolve(filePath).normalize();

            if (!Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(file.toUri());
            String contentType = Files.probeContentType(file);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

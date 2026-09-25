package com.midland.bar.Setting.Controller;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.SystemSetting;
import com.midland.bar.Setting.Repository.SystemSettingRepository;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every endpoint here is scoped to LoggerUser.getBranchUID() — the
 * branch of whoever is authenticated — never a client-supplied branch
 * ID. With ~1000 branches sharing one deployment, a user must only
 * ever be able to see or change their own branch's logo.
 */
@Slf4j
@RestController
@RequestMapping("/systemSetting")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingRepository systemSettingRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final List<String> ALLOWED_TYPES = List.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp",
            "image/svg+xml"
    );

    @GetMapping("/logo")
    public ResponseEntity<Response<SystemSetting>> getLogo() {

        String branchUid = LoggerUser.getBranchUID();

        if (branchUid == null) {
            return ResponseEntity.ok(new Response<>(new SystemSetting()));
        }

        SystemSetting settings = systemSettingRepository
                .findByBranchUid(branchUid)
                .orElseGet(SystemSetting::new);

        return ResponseEntity.ok(new Response<>(settings));
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SYSTEM_SETTINGS')")
    @PostMapping(value = "/uploadLogo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response<SystemSetting>> uploadLogo(@RequestParam("file") MultipartFile file) {

        String branchUid = LoggerUser.getBranchUID();

        if (branchUid == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response<>("No branch found for the current user"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response<>("Provide a logo image"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response<>("Only JPEG, PNG, WEBP or SVG images are allowed"));
        }

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "logo";
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalName.substring(dotIndex);
            }

            String storedName = "logo-" + branchUid + "-" + UUID.randomUUID() + extension;
            Path targetPath = uploadPath.resolve(storedName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            Optional<SystemSetting> existing = systemSettingRepository.findByBranchUid(branchUid);

            SystemSetting settings = existing.orElseGet(SystemSetting::new);
            String previousLogo = settings.getLogoImage();

            if (existing.isEmpty()) {
                settings.setBranchUid(branchUid);
            } else {
                settings.update();
            }

            settings.setLogoImage(storedName);
            settings = systemSettingRepository.save(settings);

            if (previousLogo != null && !previousLogo.isBlank()) {
                try {
                    Files.deleteIfExists(uploadPath.resolve(previousLogo));
                } catch (IOException cleanupError) {
                    log.warn("Could not remove previous logo {}", previousLogo, cleanupError);
                }
            }

            return ResponseEntity.ok(new Response<>(settings));

        } catch (IOException e) {
            log.error("Failed to store company logo", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response<>("Failed to save the logo"));
        }
    }
}

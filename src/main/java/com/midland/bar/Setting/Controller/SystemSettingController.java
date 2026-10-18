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
import java.util.Map;
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

    /**
     * The branch's own numbers: TRA details for the receipt, the cashier's
     * discount limit and the theft-alert thresholds. Empty text clears a
     * TRA field; a number left out keeps its value.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SYSTEM_SETTINGS')")
    @PostMapping("/branch")
    public ResponseEntity<Response<SystemSetting>> saveBranchSettings(@RequestBody Map<String, Object> body) {
        String branchUid = LoggerUser.getBranchUID();
        if (branchUid == null)
            return ResponseEntity.ok(new Response<>("Choose a branch first"));
        SystemSetting s = systemSettingRepository.findByBranchUid(branchUid).orElseGet(() -> {
            SystemSetting n = new SystemSetting();
            n.setBranchUid(branchUid);
            return n;
        });
        if (body.containsKey("taxTin")) {
            String tin = digits(body.get("taxTin"));
            if (tin != null && tin.length() != 9)
                return ResponseEntity.ok(new Response<>("TIN has 9 digits"));
            s.setTaxTin(tin);
        }
        if (body.containsKey("taxVrn"))
            s.setTaxVrn(text(body.get("taxVrn"), 20));
        if (body.containsKey("efdSerial"))
            s.setEfdSerial(text(body.get("efdSerial"), 40));
        try {
            if (body.get("cashierDiscountPercent") != null)
                s.setCashierDiscountPercent(between(body.get("cashierDiscountPercent"), 0, 100));
            if (body.get("alertVoidsPerShift") != null)
                s.setAlertVoidsPerShift(between(body.get("alertVoidsPerShift"), 1, 1000));
            if (body.get("alertShortageAmount") != null)
                s.setAlertShortageAmount(between(body.get("alertShortageAmount"), 0, 100_000_000));
            if (body.get("alertShiftHours") != null)
                s.setAlertShiftHours(between(body.get("alertShiftHours"), 1, 72));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(new Response<>(e.getMessage()));
        }
        return ResponseEntity.ok(new Response<>(systemSettingRepository.save(s)));
    }

    private static String digits(Object v) {
        String d = v == null ? "" : String.valueOf(v).replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String text(Object v, int max) {
        String t = v == null ? "" : String.valueOf(v).trim().toUpperCase();
        return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t);
    }

    private static int between(Object v, int min, int max) {
        int n;
        try {
            n = (int) Math.round(Double.parseDouble(String.valueOf(v)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a number");
        }
        if (n < min || n > max)
            throw new IllegalArgumentException("Enter a number between " + min + " and " + max);
        return n;
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

    /** Takes the branch's own logo off, so the BaronixTZ one shows again. */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_SYSTEM_SETTINGS')")
    @PostMapping("/removeLogo")
    public ResponseEntity<Response<SystemSetting>> removeLogo() {

        String branchUid = LoggerUser.getBranchUID();

        if (branchUid == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response<>("No branch found for the current user"));
        }

        Optional<SystemSetting> existing = systemSettingRepository.findByBranchUid(branchUid);
        if (existing.isEmpty() || existing.get().getLogoImage() == null || existing.get().getLogoImage().isBlank()) {
            return ResponseEntity.ok(new Response<>(existing.orElseGet(SystemSetting::new)));
        }

        SystemSetting settings = existing.get();
        String previousLogo = settings.getLogoImage();
        settings.update();
        settings.setLogoImage(null);
        settings = systemSettingRepository.save(settings);

        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(previousLogo));
        } catch (IOException cleanupError) {
            log.warn("Could not remove logo {}", previousLogo, cleanupError);
        }

        return ResponseEntity.ok(new Response<>(settings));
    }
}

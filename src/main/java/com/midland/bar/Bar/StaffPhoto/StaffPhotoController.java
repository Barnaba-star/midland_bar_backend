package com.midland.bar.Bar.StaffPhoto;

import com.midland.bar.Bar.Model.BarStaff;
import com.midland.bar.Bar.Repository.BarStaffRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Config.Security.StaffSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A waiter's own picture (owner, 2026-11-12): set from their Staff Sell
 * screen, shown there as its wallpaper and to customers calling a waiter
 * from a table's QR.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class StaffPhotoController {

    /** At most this many pictures each (owner, 2026-11-12). */
    public static final int MAX = 5;

    private static final Pattern FILE = Pattern.compile("^staff-[0-9a-f-]{36}\\.(jpg|png|webp)$");
    private static final Map<String, String> EXT = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            "image/webp", ".webp");

    private final BarStaffRepository barStaffRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Signed in with code + PIN: always their own (staffUid is ignored). At a
     * till opened by a manager: the staff whose code was typed, this branch only.
     */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @PostMapping(value = "/bar/staffSell/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) String staffUid) {
        String who = StaffSession.active() ? StaffSession.staffUid() : staffUid;
        if (who == null || who.isBlank())
            return ResponseEntity.badRequest().body(Map.of("code", "NO_STAFF"));
        String ext = file == null || file.isEmpty() ? null : EXT.get(file.getContentType());
        if (ext == null)
            return ResponseEntity.badRequest().body(Map.of("code", "NOT_AN_IMAGE"));
        Optional<BarStaff> found = barStaffRepository.findBarStaffByUID(who, LoggerUser.getBranchUIDOrMain());
        if (found.isEmpty())
            return ResponseEntity.status(404).body(Map.of("code", "NO_STAFF"));
        BarStaff staff = found.get();
        java.util.List<String> list = staff.photoList();
        if (list.size() >= MAX)
            return ResponseEntity.badRequest().body(Map.of("code", "MAX_PHOTOS", "message", "Picha tano tu - futa moja kwanza"));
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String name = "staff-" + UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            list.add(name);
            staff.photoList(list);
            barStaffRepository.save(staff);
            return ResponseEntity.ok(Map.of("data", Map.of("photo", "/public/staffPhoto/" + name, "photos", urls(list))));
        } catch (IOException e) {
            log.error("Could not store staff photo", e);
            return ResponseEntity.internalServerError().body(Map.of("code", "SAVE_FAILED"));
        }
    }

    /** Removes one of their pictures (the same who-rules as the upload). */
    @PreAuthorize("@authChecker.hasPermissionOrRoot('VIEW_SALES')")
    @PostMapping("/bar/staffSell/photo/remove")
    public ResponseEntity<?> remove(@RequestBody Map<String, String> body) {
        String who = StaffSession.active() ? StaffSession.staffUid() : body.get("staffUid");
        String name = body.get("photo");
        if (name != null && name.contains("/"))
            name = name.substring(name.lastIndexOf('/') + 1);
        if (who == null || who.isBlank() || name == null || !FILE.matcher(name).matches())
            return ResponseEntity.badRequest().body(Map.of("code", "BAD_REQUEST"));
        Optional<BarStaff> found = barStaffRepository.findBarStaffByUID(who, LoggerUser.getBranchUIDOrMain());
        if (found.isEmpty())
            return ResponseEntity.status(404).body(Map.of("code", "NO_STAFF"));
        BarStaff staff = found.get();
        java.util.List<String> list = staff.photoList();
        if (!list.remove(name))
            return ResponseEntity.status(404).body(Map.of("code", "NO_PHOTO"));
        staff.photoList(list);
        barStaffRepository.save(staff);
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(name));
        } catch (IOException e) {
            log.warn("Could not remove staff photo {}", name);
        }
        return ResponseEntity.ok(Map.of("data", Map.of("photos", urls(list))));
    }

    public static java.util.List<String> urls(java.util.List<String> names) {
        return names.stream().map(n -> "/public/staffPhoto/" + n).toList();
    }

    /** The picture itself - open to customers' phones; names are random, so not guessable. */
    @GetMapping("/public/staffPhoto/{name}")
    public ResponseEntity<Resource> photo(@PathVariable String name) {
        if (name == null || !FILE.matcher(name).matches())
            return ResponseEntity.notFound().build();
        Path p = Paths.get(uploadDir).resolve(name);
        if (!Files.isRegularFile(p))
            return ResponseEntity.notFound().build();
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        // A new picture gets a new name, so a copy can be kept for long.
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(p));
    }
}

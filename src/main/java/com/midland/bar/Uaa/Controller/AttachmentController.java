package com.midland.bar.Uaa.Controller;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Uaa.Dto.ProfilePicDTO;
import com.midland.bar.Uaa.Model.User;
import com.midland.bar.Uaa.Repository.UserRepository;
import com.midland.bar.Utils.Responses.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@RestController
@RequestMapping("/attachment")
@RequiredArgsConstructor
public class AttachmentController {

    private final UserRepository userRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private static final List<String> ALLOWED_TYPES = List.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"
    );

    /**
     * Self-service profile picture upload. The picture is always attached
     * to whichever user the request's JWT identifies — never a UID passed
     * by the client — so a user can only ever replace their own photo.
     */
    @PostMapping(value = "/saveAttachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response<ProfilePicDTO>> saveAttachment(@RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response<>("Provide an image file"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new Response<>("Only JPEG, PNG or WEBP images are allowed"));
        }

        try {
            User user = LoggerUser.getUser();

            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo";
            String extension = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originalName.substring(dotIndex);
            }

            String storedName = "profile-" + UUID.randomUUID() + extension;
            Path targetPath = uploadPath.resolve(storedName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Best-effort cleanup of the previous photo so the uploads
            // folder doesn't accumulate an orphaned file per change.
            String previousImage = user.getProfileImage();
            if (previousImage != null && !previousImage.isBlank()) {
                try {
                    Files.deleteIfExists(uploadPath.resolve(previousImage));
                } catch (IOException cleanupError) {
                    log.warn("Could not remove previous profile image {}", previousImage, cleanupError);
                }
            }

            user.setProfileImage(storedName);
            user.update();
            userRepository.save(user);

            ProfilePicDTO dto = new ProfilePicDTO();
            dto.setImageName(storedName);
            dto.setPath(storedName);
            dto.setType(contentType);

            // No "message" here on purpose: StatusInterceptor on the
            // frontend auto-opens a popup for any response that carries
            // one, and the profile dialog already shows its own success
            // toast — a message here would double it up.
            return ResponseEntity.ok(new Response<>(dto));

        } catch (IOException e) {
            log.error("Failed to store profile picture", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Response<>("Failed to save the image"));
        }
    }

    @GetMapping("/findUserForProfile/{userUID}")
    public ResponseEntity<Response<ProfilePicDTO>> findUserForProfile(@PathVariable String userUID) {

        Optional<User> optionalUser = userRepository.findById(userUID);

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new Response<>("User Not Found"));
        }

        User user = optionalUser.get();

        ProfilePicDTO dto = new ProfilePicDTO();
        dto.setImageName(user.getProfileImage());
        dto.setPath(user.getProfileImage());
        dto.setType(null);

        return ResponseEntity.ok(new Response<>(dto));
    }
}

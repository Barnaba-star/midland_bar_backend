package com.midland.bar.Support.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Support.Dto.GuidanceDTO;
import com.midland.bar.Support.Model.Guidance;
import com.midland.bar.Support.Repository.GuidanceRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import com.midland.bar.Utils.Responses.ResponseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Notes the platform publishes for its branches.
 *
 * A branch only sees POS, so anything it needs to be told has to arrive
 * there. This is how it gets there without a phone call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuidanceService {

    /**
     * What may be attached. Deliberately narrow: this is read on a phone by
     * somebody who did not choose to download it, so it is documents and
     * pictures, not executables and archives.
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final GuidanceRepository guidanceRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /** What a branch sees - the published ones only. */
    public ResponseList<GuidanceDTO> findPublished() {
        List<GuidanceDTO> rows = new ArrayList<>();
        for (Guidance guidance : guidanceRepository.findPublished()) {
            rows.add(toDTO(guidance));
        }
        return new ResponseList<>(rows);
    }

    /** What an admin sees - drafts included. */
    public ResponseList<GuidanceDTO> findAll() {
        log.info(LoggerUser.getEmail() + " is reading guidance");
        List<GuidanceDTO> rows = new ArrayList<>();
        for (Guidance guidance : guidanceRepository.findAllOrdered()) {
            rows.add(toDTO(guidance));
        }
        return new ResponseList<>(rows);
    }

    public Response<GuidanceDTO> save(GuidanceDTO dto) {
        log.info(LoggerUser.getEmail() + " is saving guidance");

        if (dto == null || dto.getTitle() == null || dto.getTitle().isBlank()) {
            return new Response<>("NO_TITLE");
        }

        Guidance guidance;
        if (dto.getUid() != null && !dto.getUid().isBlank()) {
            Optional<Guidance> existing = guidanceRepository.findById(dto.getUid());
            if (existing.isEmpty()) {
                return new Response<>("NOT_FOUND");
            }
            guidance = existing.get();
            guidance.update();
        } else {
            guidance = new Guidance();
        }

        guidance.setTitle(dto.getTitle().trim());
        guidance.setBody(dto.getBody() == null ? null : dto.getBody().trim());
        guidance.setVideoUrl(blankToNull(dto.getVideoUrl()));
        guidance.setPublished(Boolean.TRUE.equals(dto.getPublished()));
        guidance.setPosition(dto.getPosition() == null ? 0 : dto.getPosition());

        return new Response<>(toDTO(guidanceRepository.save(guidance)));
    }

    /**
     * Attaches a file to an existing note, replacing whatever was there.
     *
     * Kept apart from save() because a form posting JSON cannot carry a file,
     * and making every text edit re-upload one would be worse than two calls.
     */
    public Response<GuidanceDTO> attachFile(String uid, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new Response<>("NO_FILE");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            return new Response<>("BAD_TYPE");
        }

        Optional<Guidance> optional = guidanceRepository.findById(uid);
        if (optional.isEmpty()) {
            return new Response<>("NOT_FOUND");
        }
        Guidance guidance = optional.get();

        try {
            Path directory = Paths.get(uploadDir, "guidance");
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String extension = "";
            int dot = original.lastIndexOf('.');
            if (dot >= 0) {
                extension = original.substring(dot);
            }
            // Stored under a name of our own: an uploaded filename is
            // attacker-controlled, and "../../something" is a real path.
            Path target = directory.resolve(UUID.randomUUID() + extension);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // The old one is no longer referenced by anything.
            deleteQuietly(guidance.getFilePath());

            guidance.setFileName(original);
            guidance.setFilePath(target.toString());
            guidance.setFileType(file.getContentType());
            guidance.setFileSize(file.getSize());
            guidance.update();

            return new Response<>(toDTO(guidanceRepository.save(guidance)));

        } catch (IOException e) {
            log.warn("Could not store guidance file: " + e.getMessage());
            return new Response<>("STORE_FAILED");
        }
    }

    /** The file itself, for whoever is allowed to read the note. */
    public Optional<Guidance> findForDownload(String uid) {
        return guidanceRepository.findById(uid)
                .filter(g -> g.getFilePath() != null && !g.getFilePath().isBlank());
    }

    public Response<String> delete(String uid) {
        log.info(LoggerUser.getEmail() + " is deleting guidance");
        Optional<Guidance> optional = guidanceRepository.findById(uid);
        if (optional.isEmpty()) {
            return code("NOT_FOUND");
        }
        // The row goes, so nothing will ever ask for the file again.
        deleteQuietly(optional.get().getFilePath());
        guidanceRepository.delete(optional.get());
        return code("DELETED");
    }

    private static void deleteQuietly(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (Exception e) {
            // A file left behind is untidy; a failed delete that stops the
            // record being updated is worse.
            log.warn("Could not remove old guidance file: " + e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static GuidanceDTO toDTO(Guidance guidance) {
        GuidanceDTO dto = new GuidanceDTO();
        dto.setUid(guidance.getUid());
        dto.setTitle(guidance.getTitle());
        dto.setBody(guidance.getBody());
        dto.setVideoUrl(guidance.getVideoUrl());
        dto.setFileName(guidance.getFileName());
        dto.setFileType(guidance.getFileType());
        dto.setFileSize(guidance.getFileSize());
        dto.setPublished(guidance.getPublished());
        dto.setPosition(guidance.getPosition());
        dto.setCreatedAt(guidance.getCreatedAt());
        return dto;
    }

    private static Response<String> code(String value) {
        Response<String> response = new Response<>();
        response.setData(value);
        response.setStatus(ResponseStatus.SUCCESS);
        return response;
    }
}

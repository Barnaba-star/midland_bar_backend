package com.midland.bar.Support.Controller;

import com.midland.bar.Support.Dto.GuidanceDTO;
import com.midland.bar.Support.Model.Guidance;
import com.midland.bar.Support.Service.GuidanceService;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

@RestController
@RequestMapping("/guidance")
@RequiredArgsConstructor
public class GuidanceController {

    private final GuidanceService guidanceService;

    /**
     * What a branch reads. Open to anyone signed in - it is published
     * material, and the whole point is that every branch sees it.
     */
    @GetMapping("/findPublished")
    public ResponseList<GuidanceDTO> findPublished() {
        return guidanceService.findPublished();
    }

    /** The file on a note. Same reasoning as above. */
    @GetMapping("/file/{uid}")
    public ResponseEntity<?> file(@PathVariable String uid) {
        Optional<Guidance> optional = guidanceService.findForDownload(uid);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Guidance guidance = optional.get();
        var path = Paths.get(guidance.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        guidance.getFileType() == null ? "application/octet-stream" : guidance.getFileType()))
                // inline: a PDF or a picture is meant to be looked at, not
                // saved and hunted for afterwards.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (guidance.getFileName() == null ? "file" : guidance.getFileName()) + "\"")
                .body(new FileSystemResource(path));
    }

    // ============================================================
    // THE ADMIN SIDE
    // ============================================================

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_GUIDANCE')")
    @GetMapping("/findAll")
    public ResponseList<GuidanceDTO> findAll() {
        return guidanceService.findAll();
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_GUIDANCE')")
    @PostMapping("/saveGuidance")
    public Response<GuidanceDTO> save(@RequestBody GuidanceDTO dto) {
        return guidanceService.save(dto);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_GUIDANCE')")
    @PostMapping(value = "/attachFile/{uid}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<GuidanceDTO> attachFile(@PathVariable String uid,
                                            @RequestParam("file") MultipartFile file) {
        return guidanceService.attachFile(uid, file);
    }

    @PreAuthorize("@authChecker.hasPermissionOrRoot('MANAGE_GUIDANCE')")
    @PostMapping("/deleteGuidance/{uid}")
    public Response<String> delete(@PathVariable String uid) {
        return guidanceService.delete(uid);
    }
}

package com.midland.bar.Bar.AppRelease;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Android app, served from the backend's volume (UPLOAD_DIR/app/) rather
 * than from inside the website (owner, 2026-11-13): a 62 MB APK in the
 * website made every website deploy a 62 MB upload, and a weak line kept
 * cutting it. A new release is one file dropped into the volume - the
 * download page asks /public/app/latest which one is newest.
 */
@RestController
public class AppReleaseController {

    private static final Pattern NAME = Pattern.compile("^baronixtz-(\\d+)\\.(\\d+)\\.(\\d+)\\.apk$");
    private static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");

    @Value("${file.upload-dir}")
    private String uploadDir;

    private File dir() {
        return new File(uploadDir, "app");
    }

    /** {version, file, mb} of the newest APK there, or empty data when none. */
    @GetMapping("/public/app/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        File[] files = dir().listFiles((d, n) -> NAME.matcher(n).matches());
        Map<String, Object> out = new LinkedHashMap<>();
        if (files != null && files.length > 0) {
            File newest = Arrays.stream(files).max(Comparator.comparingLong(AppReleaseController::rank)).get();
            Matcher m = NAME.matcher(newest.getName());
            m.matches();
            out.put("version", m.group(1) + "." + m.group(2) + "." + m.group(3));
            out.put("file", "/public/app/" + newest.getName());
            out.put("mb", Math.round(newest.length() / 104857.6) / 10.0);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(Map.of("data", out));
    }

    @GetMapping("/public/app/{name}")
    public ResponseEntity<Resource> apk(@PathVariable String name) {
        if (!NAME.matcher(name).matches()) {
            return ResponseEntity.notFound().build();
        }
        File f = new File(dir(), name);
        if (!f.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(APK)
                .contentLength(f.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                // A version's file never changes; a new release is a new name.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(f));
    }

    private static long rank(File f) {
        Matcher m = NAME.matcher(f.getName());
        if (!m.matches()) return -1;
        return Long.parseLong(m.group(1)) * 1_000_000L + Long.parseLong(m.group(2)) * 1_000L + Long.parseLong(m.group(3));
    }
}

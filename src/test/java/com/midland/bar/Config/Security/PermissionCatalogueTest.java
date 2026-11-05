package com.midland.bar.Config.Security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every permission a controller checks must be in seed/Permission.csv: a
 * missing one is never created, so no role can get it and only root passes
 * (MANAGE_LOYALTY, 2026-11-05 - the owner's offer rule never saved).
 */
class PermissionCatalogueTest {

    private static final Pattern CHECKED = Pattern.compile("hasPermissionOrRoot\\('([A-Z_]+)'\\)");

    @Test
    void everyCheckedPermissionIsSeeded() throws IOException {
        Set<String> seeded;
        try (Stream<String> lines = Files.lines(Path.of("src/main/resources/seed/Permission.csv"))) {
            seeded = lines.map(l -> l.split(",")[0].trim()).collect(Collectors.toSet());
        }
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = CHECKED.matcher(Files.readString(f));
                while (m.find())
                    // hasPermissionOrRoot('ROOT') is root-only on purpose.
                    if (!seeded.contains(m.group(1)) && !"ROOT".equals(m.group(1)))
                        missing.add(m.group(1) + " (" + f.getFileName() + ")");
            }
        }
        assertTrue(missing.isEmpty(), "Not in seed/Permission.csv: " + missing);
    }
}

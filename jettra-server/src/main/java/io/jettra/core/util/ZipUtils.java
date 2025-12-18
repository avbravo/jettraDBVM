package io.jettra.core.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static void zipDirectory(Path sourceFolderPath, Path zipPath, List<String> exclusionPatterns)
            throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isExcluded(file, sourceFolderPath, exclusionPatterns)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path targetFile = sourceFolderPath.relativize(file);
                    zos.putNextEntry(new ZipEntry(targetFile.toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (isExcluded(dir, sourceFolderPath, exclusionPatterns)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static boolean isExcluded(Path path, Path root, List<String> patterns) {
        String relative = root.relativize(path).toString();
        // Simple check: matches start
        for (String pattern : patterns) {
            if (relative.startsWith(pattern) || relative.equals(pattern)) {
                return true;
            }
            // Check filename matches
            if (path.getFileName().toString().equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    public static void unzip(Path zipPath, Path destPath) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                Path newPath = destPath.resolve(zipEntry.getName());
                // Protect against Zip Slip
                if (!newPath.normalize().startsWith(destPath.normalize())) {
                    throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }
}

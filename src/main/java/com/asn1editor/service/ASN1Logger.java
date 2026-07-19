package com.asn1editor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dateilogger für ASN.1 Editor.
 *
 * <p>Schreibt Logeinträge in eine Datei im User-Home-Verzeichnis
 * (konfigurierbar über System Property {@code asn1editor.log.file}).
 * Unterstützt automatische Log-Rotation (max 10 MB, max 5 Dateien).
 *
 * <p>Thread-sicherheit: Alle Schreiboperationen sind synchronisiert.
 */
public final class ASN1Logger {

    private static final String LOG_DIR_NAME = ".asn1-editor";
    private static final String LOG_FILE_NAME = "asn1-editor.log";
    private static final long MAX_LOG_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_LOG_FILES = 5;

    static final Path LOG_FILE = resolveLogFile();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private ASN1Logger() {
        // nicht instanzierbar
    }

    // ─── Log-Datei Auflösung ─────────────────────────────────────────

    private static Path resolveLogFile() {
        // 1. System Property
        String customPath = System.getProperty("asn1editor.log.file");
        if (customPath != null && !customPath.isBlank()) {
            return Path.of(customPath);
        }

        // 2. User-Home-Verzeichnis
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return Path.of(LOG_FILE_NAME); // Fallback
        }

        Path logDir = Path.of(userHome, LOG_DIR_NAME);
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            System.err.println("Konnte Log-Verzeichnis nicht erstellen: " + logDir + " — verwende " + LOG_FILE_NAME);
            return Path.of(LOG_FILE_NAME);
        }

        return logDir.resolve(LOG_FILE_NAME);
    }

    // ─── Log-Rotation ────────────────────────────────────────────────

    private static synchronized void rotateIfNeeded() {
        if (!Files.exists(LOG_FILE)) {
            return;
        }

        try {
            long size = Files.size(LOG_FILE);
            if (size <= MAX_LOG_SIZE_BYTES) {
                return;
            }

            // Alte Logs umbenennen
            List<Path> existing = Files.list(LOG_FILE.getParent())
                    .filter(p -> p.getFileName().toString().startsWith("asn1-editor"))
                    .sorted(Comparator.comparingLong(p -> { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return Long.MAX_VALUE; } }))
                    .collect(Collectors.toList());

            // Älteste löschen wenn > MAX_LOG_FILES
            while (existing.size() >= MAX_LOG_FILES) {
                Path oldest = existing.remove(0);
                Files.delete(oldest);
            }

            // Aktuelle Log-Datei umbenennen
            Path rotated = LOG_FILE.getParent().resolve("asn1-editor.log.1");
            if (Files.exists(rotated)) {
                Files.delete(rotated);
            }
            Files.move(LOG_FILE, rotated);
        } catch (IOException e) {
            System.err.println("Log-Rotation fehlgeschlagen: " + e.getMessage());
        }
    }

    // ─── Öffentliche API ─────────────────────────────────────────────

    /**
     * Loggt eine Datei-Lade-Operation.
     */
    public static void logFileLoad(Path path, String format, long size) {
        String msg = String.format("Datei geladen: %-40s Format: %-10s Größe: %d bytes",
                path != null ? path.getFileName() : "(null)", format, size);
        info(msg);
    }

    /**
     * Loggt den Decodierungsprozess.
     */
    public static void logDecoding(boolean decoded, int originalLength, int decodedLength) {
        if (decoded) {
            info(String.format("Base64 decodiert: %d → %d Zeichen", originalLength, decodedLength));
        } else {
            info(String.format("Keine Base64-Erkennung (Datei: %d Zeichen)", originalLength));
        }
    }

    /**
     * Loggt eine Parsing-Operation.
     */
    public static void logParsing(boolean success, String message) {
        if (success) {
            info(String.format("Parsing erfolgreich: %s", message));
        } else {
            warn(String.format("Parsing fehlgeschlagen: %s", message));
        }
    }

    /**
     * Loggt eine Datei-Speicher-Operation.
     */
    public static void logFileSave(Path path, long size) {
        info(String.format("Datei gespeichert: %-40s Größe: %d bytes",
                path != null ? path.getFileName() : "(null)", size));
    }

    /**
     * Loggt einen Fehler.
     */
    public static void logError(String operation, Throwable error) {
        String msg = String.format("[%s] Fehler: %s", operation, error.getMessage());
        error(msg);
        if (error != null) {
            error.printStackTrace();
        }
    }

    /**
     * Loggt ein Hex-Snippet der Binärdatei.
     */
    public static void logBinarySnippet(byte[] data, int maxBytes) {
        int len = Math.min(data.length, maxBytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0 && i % 16 == 0) sb.append("  ");
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        info(String.format("Binär-Inhalt (Hex, erste %d Bytes): %s%s", len, sb.toString(),
                data.length > maxBytes ? " ..." : ""));
    }

    // ─── Interner Logging-Teil ────────────────────────────────────────

    /**
     * Schreibt einen INFO-Eintrag in die Log-Datei.
     */
    static void info(String msg) {
        String line = String.format("[%s] [INFO]  %s", LocalDateTime.now().format(FMT), msg);
        writeLine(line);
    }

    /**
     * Schreibt einen WARN-Eintrag in die Log-Datei und auf stderr.
     */
    static void warn(String msg) {
        String line = String.format("[%s] [WARN]  %s", LocalDateTime.now().format(FMT), msg);
        writeLine(line);
        System.err.println(line);
    }

    /**
     * Schreibt einen ERROR-Eintrag in die Log-Datei und auf stderr.
     */
    private static void error(String msg) {
        String line = String.format("[%s] [ERROR] %s", LocalDateTime.now().format(FMT), msg);
        writeLine(line);
        System.err.println(line);
    }

    private static synchronized void writeLine(String line) {
        try {
            rotateIfNeeded();
            Files.writeString(LOG_FILE, line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Konnte nicht in Log-Datei schreiben: " + e.getMessage());
        }
    }
}

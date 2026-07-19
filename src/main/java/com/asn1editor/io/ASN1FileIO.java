package com.asn1editor.io;

import com.asn1editor.service.ASN1IOException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dateihandling für ASN.1-Dateien.
 *
 * <p>Kapselt das Laden und Speichern von ASN.1-Quelltexten.
 * Parser-Logik ist hier nicht enthalten — das ist Aufgabe von {@link com.asn1editor.service.ASN1Service}.
 */
public class ASN1FileIO {

    /**
     * Liest den Inhalt einer ASN.1-Datei byte-erhaltend.
     *
     * @param path Pfad zur Datei
     * @return der Dateinhalt als ISO-8859-1-String, damit Binärbytes 1:1 erhalten bleiben
     * @throws ASN1IOException wenn die Datei nicht gelesen werden kann
     */
    public String readFile(Path path) throws ASN1IOException {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new ASN1IOException(
                    "Datei konnte nicht gelesen werden: ",
                    path != null ? path.toString() : null,
                    e);
        }
    }

    /**
     * Schreibt den gegebenen Inhalt in eine ASN.1-Datei.
     *
     * @param path Pfad zur Zieldatei
     * @param content der zu schreibende ASN.1-Quelltext
     * @throws ASN1IOException wenn die Datei nicht geschrieben werden kann
     */
    public void writeFile(Path path, String content) throws ASN1IOException {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ASN1IOException(
                    "Datei konnte nicht geschrieben werden: ",
                    path != null ? path.toString() : null,
                    e);
        }
    }
}

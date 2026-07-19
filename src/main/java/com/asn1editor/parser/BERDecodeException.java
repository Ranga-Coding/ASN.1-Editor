package com.asn1editor.parser;

/**
 * Exception für Fehler bei der BER/DER-Decodierung.
 *
 * <p>Speichert die Byte-Position im Binärstrom, um präzise Fehlermeldungen
 * im Hex-Editor zu ermöglichen.
 */
public class BERDecodeException extends Exception {
    private final int byteOffset;
    private final int byteLength;

    /**
     * Erstellt eine BERDecodeException.
     *
     * @param message  die Fehlerbeschreibung
     * @param byteOffset der Byte-Offset im Binärstrom, an dem der Fehler auftrat
     */
    public BERDecodeException(String message, int byteOffset) {
        super(formatMessage(message, byteOffset, 0));
        this.byteOffset = byteOffset;
        this.byteLength = 0;
    }

    /**
     * Erstellt eine BERDecodeException mit einem Byte-Bereich.
     *
     * @param message  die Fehlerbeschreibung
     * @param byteOffset der Start-Byte-Offset im Binärstrom
     * @param byteLength die Länge des betroffenen Bereichs
     */
    public BERDecodeException(String message, int byteOffset, int byteLength) {
        super(formatMessage(message, byteOffset, byteLength));
        this.byteOffset = byteOffset;
        this.byteLength = byteLength;
    }

    private static String formatMessage(String message, int byteOffset, int byteLength) {
        if (byteLength > 0) {
            return String.format("BER/DER-Decodierungsfehler bei Byte %d-%d: %s",
                    byteOffset, byteOffset + byteLength - 1, message);
        }
        return String.format("BER/DER-Decodierungsfehler bei Byte %d: %s", byteOffset, message);
    }

    /**
     * Gibt den Byte-Offset zurück, an dem der Fehler auftrat.
     */
    public int getByteOffset() {
        return byteOffset;
    }

    /**
     * Gibt die Länge des betroffenen Byte-Bereichs zurück.
     */
    public int getByteLength() {
        return byteLength;
    }
}

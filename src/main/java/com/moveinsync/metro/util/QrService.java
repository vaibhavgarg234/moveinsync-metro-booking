package com.moveinsync.metro.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Generates and validates tamper-resistant, HMAC-SHA256-signed QR tokens.
 *
 * <p>Format (before base64url encoding):
 * <pre>{bookingId}|{source}|{destination}|{timestampEpoch}|{hmac}</pre>
 *
 * <p>The secret key is read from {@code metro.qr.secret-key} in application.properties.
 */
@Component
@Slf4j
public class QrService {

    private static final String HMAC_ALGO = "HmacSHA256";

    @Value("${metro.qr.secret-key}")
    private String secretKey;

    /**
     * Generates a base64url-encoded, HMAC-signed QR token.
     */
    public String generateQrString(String bookingId, String sourceStopId, String destStopId) {
        long ts = System.currentTimeMillis() / 1000L;
        String payload = String.join("|", bookingId, sourceStopId, destStopId, String.valueOf(ts));
        String sig = sign(payload);
        String raw = payload + "|" + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates the QR token and returns a result record.
     */
    public ValidationResult validateQrString(String qrString) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(qrString));
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 5);

            if (parts.length != 5) {
                return new ValidationResult(false, null, "Malformed token");
            }

            String bookingId = parts[0];
            String source    = parts[1];
            String dest      = parts[2];
            String ts        = parts[3];
            String givenSig  = parts[4];

            String payload  = String.join("|", bookingId, source, dest, ts);
            String expected = sign(payload);

            if (!constantTimeEqual(givenSig, expected)) {
                return new ValidationResult(false, null, "Signature mismatch – tampered token");
            }
            return new ValidationResult(true, bookingId, "OK");

        } catch (Exception e) {
            log.warn("QR decode error: {}", e.getMessage());
            return new ValidationResult(false, null, "Decode error: " + e.getMessage());
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEqual(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String padBase64(String s) {
        int pad = s.length() % 4;
        return pad == 0 ? s : s + "=".repeat(4 - pad);
    }

    public record ValidationResult(boolean valid, String bookingId, String reason) {}
}
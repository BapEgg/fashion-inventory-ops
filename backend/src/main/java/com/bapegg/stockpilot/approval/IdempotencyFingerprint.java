package com.bapegg.stockpilot.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

/**
 * Deterministic SHA-256 fingerprint over an approval command's normalized fields, per
 * {@code knowledge/business-rules.md} section 10's idempotency contract. No fingerprint
 * column exists in the schema -- a fingerprint is only ever computed in memory, both for
 * an incoming request and for the command reconstructed from an existing decision
 * sharing the same {@code decision_request_id}, so the two can be compared.
 */
public final class IdempotencyFingerprint {

    private static final String CONTRACT_VERSION = "MVP-2";

    private IdempotencyFingerprint() {
    }

    /** NFC + {@code strip()}; a blank result becomes {@code null} (the "optional string" contract). */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * The fixed-order, length-prefixed canonical encoding (excluding the key itself)
     * described in section 10: {@code name:UTF-8-byte-length:value\n} per field, with a
     * length of {@code -1} and an empty value standing in for {@code null} (distinguishing
     * "absent" from a genuine empty string). Numbers are unsigned standard decimal,
     * {@code boolean} is {@code true}/{@code false}, enums are their {@code name()}.
     */
    public static String compute(
            Long recommendationId,
            Long analysisRunId,
            String inputSnapshotVersion,
            String ruleVersion,
            int candidateVersion,
            String status,
            Integer selectedQuantity,
            boolean policyException,
            String reasonCode,
            String reason,
            String actorLabel) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, "contractVersion", CONTRACT_VERSION);
        appendField(canonical, "recommendationId", String.valueOf(recommendationId));
        appendField(canonical, "analysisRunId", String.valueOf(analysisRunId));
        appendField(canonical, "inputSnapshotVersion", inputSnapshotVersion);
        appendField(canonical, "ruleVersion", ruleVersion);
        appendField(canonical, "candidateVersion", String.valueOf(candidateVersion));
        appendField(canonical, "status", status);
        appendField(canonical, "selectedQuantity", selectedQuantity == null ? null : String.valueOf(selectedQuantity));
        appendField(canonical, "policyException", String.valueOf(policyException));
        appendField(canonical, "reasonCode", reasonCode);
        appendField(canonical, "reason", reason);
        appendField(canonical, "actorLabel", actorLabel);
        return sha256Hex(canonical.toString());
    }

    private static void appendField(StringBuilder canonical, String name, String value) {
        int length = value == null ? -1 : value.getBytes(StandardCharsets.UTF_8).length;
        canonical.append(name).append(':').append(length).append(':');
        if (value != null) {
            canonical.append(value);
        }
        canonical.append('\n');
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM.", e);
        }
    }
}

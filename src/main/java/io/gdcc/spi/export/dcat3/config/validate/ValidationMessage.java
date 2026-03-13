package io.gdcc.spi.export.dcat3.config.validate;

import java.util.Objects;

/**
 * @param code e.g., DCATCFG-001
 * @param path e.g., root.elements[id=dcatDataset]
 * @param message human-readable
 * @param hint optional remediation hint
 */
public record ValidationMessage(Severity severity, String code, String path, String message, String hint) {
    public ValidationMessage(Severity severity, String code, String path, String message, String hint) {
        this.severity = Objects.requireNonNull(severity);
        this.code = code;
        this.path = path;
        this.message = message;
        this.hint = hint;
    }

    @Override
    public String toString() {
        return severity
                + " "
                + (code != null ? code + " " : "")
                + (path != null ? path + ": " : "")
                + message
                + (hint != null ? " (hint: " + hint + ")" : "");
    }
}

package com.hr.recruitment.common;

import java.util.List;

/**
 * RFC 7807 problem+json error body. Shared by every feature package.
 */
public record ProblemDetail(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String requestId,
    List<FieldError> errors
) {

    public record FieldError(String field, String message) {}

    public static final String TYPE_VALIDATION = "https://hr-recruitment/errors/validation";
    public static final String TYPE_NOT_FOUND = "https://hr-recruitment/errors/not-found";
    public static final String TYPE_INVALID_TRANSITION = "https://hr-recruitment/errors/invalid-status-transition";
    public static final String TYPE_VERSION_CONFLICT = "https://hr-recruitment/errors/version-conflict";
    public static final String TYPE_MALFORMED_REQUEST = "https://hr-recruitment/errors/malformed-request";

    public static ProblemDetail of(String type, String title, int status, String detail, String instance, String requestId) {
        return new ProblemDetail(type, title, status, detail, instance, requestId, null);
    }

    public static ProblemDetail validation(String instance, String requestId, List<FieldError> errors) {
        return new ProblemDetail(TYPE_VALIDATION, "Validation failed", 400,
            errors.size() + " field" + (errors.size() == 1 ? "" : "s") + " " + (errors.size() == 1 ? "is" : "are") + " invalid",
            instance, requestId, errors);
    }
}

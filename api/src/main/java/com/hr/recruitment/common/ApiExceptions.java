package com.hr.recruitment.common;

import java.util.List;

/**
 * Typed exceptions that the Router/Controller layer maps to a {@link ProblemDetail}.
 * Never carries a raw field value in its message — only the field name, per the
 * "no PII in error messages" rule (candidate spec, non-functional requirements).
 */
public final class ApiExceptions {

    private ApiExceptions() {}

    public static final class ValidationException extends RuntimeException {
        private final List<ProblemDetail.FieldError> errors;

        public ValidationException(List<ProblemDetail.FieldError> errors) {
            super("Validation failed for " + errors.size() + " field(s)");
            this.errors = errors;
        }

        public List<ProblemDetail.FieldError> errors() {
            return errors;
        }
    }

    public static final class NotFoundException extends RuntimeException {
        public NotFoundException(String resource, String id) {
            super(resource + " not found: " + id);
        }
    }

    public static final class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }

    public static final class VersionConflictException extends RuntimeException {
        public VersionConflictException(String message) {
            super(message);
        }
    }

    public static final class MalformedRequestException extends RuntimeException {
        public MalformedRequestException(String message) {
            super(message);
        }
    }
}

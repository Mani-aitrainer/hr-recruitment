package com.hr.recruitment.candidate.dto;

import com.hr.recruitment.candidate.model.HighestQualification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for POST and PUT. Field rules match docs/specs/candidate.spec.md exactly.
 * `version` is required on PUT only; CandidateService.update checks it is present there.
 */
public record CandidateRequest(
    @NotBlank(message = "is required")
    @Size(min = 2, max = 100, message = "must be 2-100 characters")
    String fullName,

    @NotBlank(message = "is required")
    @Size(max = 255, message = "must be at most 255 characters")
    @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "must be a valid email address")
    String email,

    @NotBlank(message = "is required")
    @Size(min = 8, max = 20, message = "must be 8-20 characters")
    @Pattern(regexp = "^\\+?[0-9 \\-]+$", message = "must contain only digits, spaces, hyphens, and an optional leading +")
    String phone,

    @NotBlank(message = "is required")
    @Size(min = 2, max = 100, message = "must be 2-100 characters")
    String location,

    // Optional: blank/absent is fine, but a non-blank value must be 2-100 chars, matching
    // the DB's `CHECK (current_employer IS NULL OR char_length(...) BETWEEN 2 AND 100)`.
    @Pattern(regexp = "^$|^.{2,100}$", message = "must be 2-100 characters when present")
    String currentEmployer,

    @Pattern(regexp = "^$|^.{2,100}$", message = "must be 2-100 characters when present")
    String currentTitle,

    @NotNull(message = "is required")
    @Min(value = 0, message = "must be between 0 and 40")
    @Max(value = 40, message = "must be between 0 and 40")
    Integer totalExperienceYears,

    @Min(value = 0, message = "must be between 0 and 180")
    @Max(value = 180, message = "must be between 0 and 180")
    Integer noticePeriodDays,

    @NotNull(message = "is required")
    HighestQualification highestQualification,

    @NotNull(message = "is required")
    @Size(min = 1, max = 15, message = "must have 1-15 items")
    List<@NotBlank @Size(min = 1, max = 30, message = "each skill must be 1-30 characters") String> skills,

    @Size(max = 2000, message = "must be at most 2000 characters")
    String summary,

    /** Required on PUT (optimistic locking; checked in CandidateService.update), ignored on POST. */
    Integer version
) {}

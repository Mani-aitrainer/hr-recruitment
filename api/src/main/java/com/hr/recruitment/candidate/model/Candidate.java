package com.hr.recruitment.candidate.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain record. `deletedAt` is never serialized in a response — soft-deleted rows return 404. */
public record Candidate(
    UUID id,
    String fullName,
    String email,
    String phone,
    String location,
    String currentEmployer,
    String currentTitle,
    int totalExperienceYears,
    Integer noticePeriodDays,
    HighestQualification highestQualification,
    List<String> skills,
    String summary,
    CandidateStatus status,
    int version,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt,
    Instant deletedAt
) {}

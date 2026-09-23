package com.hr.recruitment.candidate.dto;

import com.hr.recruitment.candidate.model.Candidate;
import com.hr.recruitment.candidate.model.HighestQualification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response body. Never includes `deletedAt` — soft-deleted candidates are 404, not shown. */
public record CandidateResponse(
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
    String status,
    int version,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt
) {
    public static CandidateResponse from(Candidate c) {
        return new CandidateResponse(
            c.id(), c.fullName(), c.email(), c.phone(), c.location(),
            c.currentEmployer(), c.currentTitle(), c.totalExperienceYears(), c.noticePeriodDays(),
            c.highestQualification(), c.skills(), c.summary(), c.status().name(), c.version(),
            c.createdAt(), c.updatedAt(), c.archivedAt()
        );
    }
}

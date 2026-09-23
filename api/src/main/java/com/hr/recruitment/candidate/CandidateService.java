package com.hr.recruitment.candidate;

import com.hr.recruitment.candidate.dto.CandidateRequest;
import com.hr.recruitment.candidate.model.Candidate;
import com.hr.recruitment.candidate.model.CandidateStatus;
import com.hr.recruitment.common.ApiExceptions.InvalidStatusTransitionException;
import com.hr.recruitment.common.ApiExceptions.NotFoundException;
import com.hr.recruitment.common.ApiExceptions.ValidationException;
import com.hr.recruitment.common.ApiExceptions.VersionConflictException;
import com.hr.recruitment.common.ProblemDetail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Business rules and the ACTIVE/ARCHIVED status guard. No SQL here — only CandidateRepository
 * talks to the database. Throws typed exceptions the Router maps to problem+json.
 */
public final class CandidateService {

    private final CandidateRepository repository;

    public CandidateService(CandidateRepository repository) {
        this.repository = repository;
    }

    public Candidate create(CandidateRequest request) {
        validateSkillsUniqueness(request.skills());
        Candidate toInsert = new Candidate(
            null,
            request.fullName().trim(),
            request.email().trim().toLowerCase(Locale.ROOT),
            request.phone().trim(),
            request.location().trim(),
            blankToNull(request.currentEmployer()),
            blankToNull(request.currentTitle()),
            request.totalExperienceYears(),
            request.noticePeriodDays(),
            request.highestQualification(),
            request.skills(),
            blankToNull(request.summary()),
            CandidateStatus.ACTIVE,
            0, null, null, null, null
        );
        return repository.insert(toInsert);
    }

    public Candidate getById(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Candidate", id.toString()));
    }

    public Candidate update(UUID id, CandidateRequest request) {
        if (request.version() == null) {
            throw new ValidationException(List.of(
                new ProblemDetail.FieldError("version", "is required")));
        }
        validateSkillsUniqueness(request.skills());

        // Confirm the candidate exists and is ACTIVE before attempting the optimistic-lock update,
        // so a request against an ARCHIVED candidate gets 409 invalid-status-transition rather than
        // being silently swallowed by the "no row matched" branch below.
        Candidate existing = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Candidate", id.toString()));
        if (existing.status() != CandidateStatus.ACTIVE) {
            throw new InvalidStatusTransitionException("Only an ACTIVE candidate can be edited");
        }

        Candidate toUpdate = new Candidate(
            id,
            request.fullName().trim(),
            request.email().trim().toLowerCase(Locale.ROOT),
            request.phone().trim(),
            request.location().trim(),
            blankToNull(request.currentEmployer()),
            blankToNull(request.currentTitle()),
            request.totalExperienceYears(),
            request.noticePeriodDays(),
            request.highestQualification(),
            request.skills(),
            blankToNull(request.summary()),
            existing.status(),
            existing.version(), null, null, null, null
        );

        return repository.updateIfVersionMatches(id, request.version(), toUpdate)
            .orElseGet(() -> {
                // No row matched id+version+deleted_at IS NULL. Distinguish the two ways that
                // can happen: the candidate was soft-deleted between the check above and this
                // update (404, not a version conflict), versus the version really is stale (409).
                if (repository.findById(id).isEmpty()) {
                    throw new NotFoundException("Candidate", id.toString());
                }
                throw new VersionConflictException("Candidate " + id + " was modified by another request");
            });
    }

    /** skills are 1-15 items, each 1-30 chars (enforced by bean validation); this enforces
     *  the case-insensitive uniqueness rule that a CHECK constraint cannot express. */
    private void validateSkillsUniqueness(List<String> skills) {
        List<String> lower = new ArrayList<>();
        for (String skill : skills) {
            String normalized = skill.trim().toLowerCase(Locale.ROOT);
            if (lower.contains(normalized)) {
                throw new ValidationException(List.of(
                    new ProblemDetail.FieldError("skills", "must not contain duplicate values (case-insensitive)")));
            }
            lower.add(normalized);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

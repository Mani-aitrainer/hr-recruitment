package com.hr.recruitment.candidate;

import com.hr.recruitment.candidate.dto.CandidateRequest;
import com.hr.recruitment.candidate.model.Candidate;
import com.hr.recruitment.candidate.model.CandidateStatus;
import com.hr.recruitment.candidate.model.HighestQualification;
import com.hr.recruitment.common.ApiExceptions.InvalidStatusTransitionException;
import com.hr.recruitment.common.ApiExceptions.NotFoundException;
import com.hr.recruitment.common.ApiExceptions.ValidationException;
import com.hr.recruitment.common.ApiExceptions.VersionConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository repository;

    private CandidateService service;

    private CandidateService newService() {
        return new CandidateService(repository);
    }

    private CandidateRequest validRequest() {
        return new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            "Acme Corp", "Engineer", 5, 30, HighestQualification.BACHELORS,
            List.of("Java", "SQL"), "A summary", null);
    }

    private Candidate activeCandidate(UUID id, int version) {
        return new Candidate(id, "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            "Acme Corp", "Engineer", 5, 30, HighestQualification.BACHELORS,
            List.of("Java", "SQL"), "A summary", CandidateStatus.ACTIVE, version,
            Instant.now(), Instant.now(), null, null);
    }

    // ---- CAND-1: positive, create ----
    @Test
    void CAND_1_creates_candidate_with_active_status_and_version_one() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.insert(any())).thenReturn(activeCandidate(id, 1));

        Candidate result = service.create(validRequest());

        assertThat(result.status()).isEqualTo(CandidateStatus.ACTIVE);
        assertThat(result.version()).isEqualTo(1);
        verify(repository).insert(any());
    }

    @Test
    void create_lowercases_and_trims_email() {
        service = newService();
        CandidateRequest request = new CandidateRequest(
            "  Asha Rao  ", "  Asha.Rao@EXAMPLE.com  ", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS,
            List.of("Java"), null, null);
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        Candidate result = service.create(request);

        assertThat(result.fullName()).isEqualTo("Asha Rao");
        assertThat(result.email()).isEqualTo("asha.rao@example.com");
    }

    // ---- CAND-5: negative, duplicate skills (case-insensitive) ----
    @Test
    void CAND_5_rejects_case_insensitive_duplicate_skills() {
        service = newService();
        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS,
            List.of("Java", "java"), null, null);

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("skills")));
    }

    // ---- CAND-6: positive, edit ----
    @Test
    void CAND_6_updates_active_candidate_and_increments_version() {
        service = newService();
        UUID id = UUID.randomUUID();
        Candidate existing = activeCandidate(id, 1);
        Candidate updated = activeCandidate(id, 2);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.updateIfVersionMatches(eq(id), eq(1), any())).thenReturn(Optional.of(updated));

        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            "New Co", "Senior Engineer", 6, 15, HighestQualification.MASTERS,
            List.of("Java"), null, 1);

        Candidate result = service.update(id, request);

        assertThat(result.version()).isEqualTo(2);
    }

    // ---- CAND-7: negative, stale version -> 409 version-conflict ----
    @Test
    void CAND_7_rejects_stale_version_with_conflict() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(activeCandidate(id, 3)));
        when(repository.updateIfVersionMatches(eq(id), eq(1), any())).thenReturn(Optional.empty());

        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null, 1);

        assertThatThrownBy(() -> service.update(id, request))
            .isInstanceOf(VersionConflictException.class);
    }

    // ---- negative: candidate soft-deleted between the status check and the update -> 404, not 409 ----
    @Test
    void update_reports_not_found_when_candidate_is_deleted_between_check_and_update() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id))
            .thenReturn(Optional.of(activeCandidate(id, 1)))  // the pre-update existence/status check
            .thenReturn(Optional.empty());                     // the re-read after the update matched no row
        when(repository.updateIfVersionMatches(eq(id), eq(1), any())).thenReturn(Optional.empty());

        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null, 1);

        assertThatThrownBy(() -> service.update(id, request))
            .isInstanceOf(NotFoundException.class);
    }

    // ---- CAND-8: negative, unknown id -> 404, for both getById and update ----
    @Test
    void CAND_8_getById_throws_not_found_for_unknown_id() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void CAND_8_update_throws_not_found_for_unknown_id() {
        service = newService();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null, 0);

        assertThatThrownBy(() -> service.update(id, request))
            .isInstanceOf(NotFoundException.class);
    }

    // ---- negative: version missing on PUT ----
    @Test
    void update_rejects_missing_version() {
        service = newService();
        UUID id = UUID.randomUUID();
        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null, null);

        assertThatThrownBy(() -> service.update(id, request))
            .isInstanceOf(ValidationException.class);
    }

    // ---- negative: editing an ARCHIVED candidate -> 409 invalid-status-transition ----
    @Test
    void update_rejects_edit_of_archived_candidate() {
        service = newService();
        UUID id = UUID.randomUUID();
        Candidate archived = new Candidate(id, "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null,
            CandidateStatus.ARCHIVED, 2, Instant.now(), Instant.now(), Instant.now(), null);
        when(repository.findById(id)).thenReturn(Optional.of(archived));

        CandidateRequest request = new CandidateRequest(
            "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null, 2);

        assertThatThrownBy(() -> service.update(id, request))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }
}

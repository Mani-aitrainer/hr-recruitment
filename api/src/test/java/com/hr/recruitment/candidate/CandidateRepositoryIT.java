package com.hr.recruitment.candidate;

import com.hr.recruitment.candidate.model.Candidate;
import com.hr.recruitment.candidate.model.CandidateStatus;
import com.hr.recruitment.candidate.model.HighestQualification;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies db/migrations (copied to src/main/resources/db/migration at build time) to a real
 * PostgreSQL 17 container and exercises CandidateRepository against it.
 */
@Testcontainers
class CandidateRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("hr")
        .withUsername("postgres")
        .withPassword("dev");

    static HikariDataSource dataSource;
    static CandidateRepository repository;

    @BeforeAll
    static void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        repository = new CandidateRepository(dataSource);
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
    }

    private Candidate sample(String email) {
        return new Candidate(null, "Asha Rao", email, "9876543210", "Bengaluru",
            "Acme Corp", "Engineer", 5, 30, HighestQualification.BACHELORS,
            List.of("Java", "SQL"), "A summary", CandidateStatus.ACTIVE, 0, null, null, null, null);
    }

    @Test
    void insert_then_findById_returns_the_candidate() {
        Candidate inserted = repository.insert(sample("insert-find@example.com"));

        Optional<Candidate> found = repository.findById(inserted.id());

        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("insert-find@example.com");
        assertThat(found.get().version()).isEqualTo(1);
    }

    @Test
    void findById_returns_empty_for_unknown_id() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateIfVersionMatches_updates_and_increments_version_when_version_matches() {
        Candidate inserted = repository.insert(sample("update-match@example.com"));

        Candidate toUpdate = new Candidate(inserted.id(), "Asha Rao Updated", inserted.email(),
            inserted.phone(), inserted.location(), inserted.currentEmployer(), "Lead Engineer",
            6, 15, HighestQualification.MASTERS, List.of("Java"), null, CandidateStatus.ACTIVE,
            0, null, null, null, null);

        Optional<Candidate> updated = repository.updateIfVersionMatches(inserted.id(), 1, toUpdate);

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().fullName()).isEqualTo("Asha Rao Updated");
        assertThat(updated.get().currentTitle()).isEqualTo("Lead Engineer");
    }

    @Test
    void updateIfVersionMatches_returns_empty_when_version_is_stale() {
        Candidate inserted = repository.insert(sample("stale-version@example.com"));

        Candidate toUpdate = new Candidate(inserted.id(), "Someone Else", inserted.email(),
            inserted.phone(), inserted.location(), null, null, 5, null,
            HighestQualification.BACHELORS, List.of("Java"), null, CandidateStatus.ACTIVE,
            0, null, null, null, null);

        Optional<Candidate> result = repository.updateIfVersionMatches(inserted.id(), 99, toUpdate);

        assertThat(result).isEmpty();
    }

    @Test
    void findById_returns_empty_for_soft_deleted_row() throws Exception {
        Candidate inserted = repository.insert(sample("soft-deleted@example.com"));
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("UPDATE candidate SET deleted_at = now() WHERE id = ?")) {
            stmt.setObject(1, inserted.id());
            stmt.executeUpdate();
        }

        assertThat(repository.findById(inserted.id())).isEmpty();
    }

    @Test
    void constraint_rejects_experience_out_of_range() {
        Candidate invalid = new Candidate(null, "Asha Rao", "range-check@example.com", "9876543210",
            "Bengaluru", null, null, 41, null, HighestQualification.BACHELORS,
            List.of("Java"), null, CandidateStatus.ACTIVE, 0, null, null, null, null);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> repository.insert(invalid));
    }

    /**
     * The remaining CHECK constraints (db/migrations/V1__create_candidate.sql, also proven
     * directly in db/checks.sql): every insert here must be rejected by the database.
     */
    @Test
    void constraint_rejects_short_full_name() {
        Candidate invalid = new Candidate(null, "A", "short-name@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of("Java"), null,
            CandidateStatus.ACTIVE, 0, null, null, null, null);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> repository.insert(invalid));
    }

    @Test
    void constraint_rejects_unknown_highest_qualification() throws Exception {
        // HighestQualification is a closed Java enum, so an "unknown" value can only reach the
        // database through a path that bypasses it — this proves the CHECK still catches it if
        // that ever happens (e.g. a future migration adds a value the enum doesn't know yet).
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                 INSERT INTO candidate (full_name, email, phone, location, total_experience_years,
                     highest_qualification, skills)
                 VALUES ('Asha Rao', 'unknown-qual@example.com', '9876543210', 'Bengaluru', 5, 'PHD_EXTRA', ARRAY['Java'])
                 """)) {
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, stmt::executeUpdate);
        }
    }

    @Test
    void constraint_rejects_empty_skills() {
        Candidate invalid = new Candidate(null, "Asha Rao", "empty-skills@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, HighestQualification.BACHELORS, List.of(), null,
            CandidateStatus.ACTIVE, 0, null, null, null, null);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> repository.insert(invalid));
    }
}

package com.hr.recruitment.candidate;

import com.hr.recruitment.candidate.model.Candidate;
import com.hr.recruitment.candidate.model.CandidateStatus;
import com.hr.recruitment.candidate.model.HighestQualification;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for `candidate`. Prepared statements only. Every read filters out
 * soft-deleted rows (deleted_at IS NULL). Updates use optimistic locking on `version`.
 */
public final class CandidateRepository {

    private final DataSource dataSource;

    public CandidateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Candidate insert(Candidate c) {
        String sql = """
            INSERT INTO candidate (full_name, email, phone, location, current_employer, current_title,
                total_experience_years, notice_period_days, highest_qualification, skills, summary, status, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            RETURNING id, created_at, updated_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.fullName());
            ps.setString(2, c.email());
            ps.setString(3, c.phone());
            ps.setString(4, c.location());
            ps.setString(5, c.currentEmployer());
            ps.setString(6, c.currentTitle());
            ps.setInt(7, c.totalExperienceYears());
            setNullableInt(ps, 8, c.noticePeriodDays());
            ps.setString(9, c.highestQualification().name());
            ps.setArray(10, toSqlArray(conn, c.skills()));
            ps.setString(11, c.summary());
            ps.setString(12, c.status().name());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                UUID id = (UUID) rs.getObject("id");
                var createdAt = rs.getTimestamp("created_at").toInstant();
                var updatedAt = rs.getTimestamp("updated_at").toInstant();
                return new Candidate(id, c.fullName(), c.email(), c.phone(), c.location(),
                    c.currentEmployer(), c.currentTitle(), c.totalExperienceYears(), c.noticePeriodDays(),
                    c.highestQualification(), c.skills(), c.summary(), c.status(), 1,
                    createdAt, updatedAt, null, null);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert candidate", e);
        }
    }

    public Optional<Candidate> findById(UUID id) {
        String sql = """
            SELECT id, full_name, email, phone, location, current_employer, current_title,
                   total_experience_years, notice_period_days, highest_qualification, skills, summary,
                   status, version, created_at, updated_at, archived_at, deleted_at
            FROM candidate
            WHERE id = ? AND deleted_at IS NULL
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load candidate", e);
        }
    }

    /**
     * Updates the row only if id matches and deleted_at IS NULL and version matches
     * (optimistic locking). Returns empty if no row matched — the caller distinguishes
     * "not found" (including soft-deleted) from "stale version" by re-reading.
     *
     * Builds the returned Candidate from the RETURNING row itself rather than a second
     * findById() on a different pooled connection, so the response can never show values
     * written by a concurrent request that landed between the UPDATE and a re-read.
     */
    public Optional<Candidate> updateIfVersionMatches(UUID id, int expectedVersion, Candidate c) {
        String sql = """
            UPDATE candidate SET
                full_name = ?, email = ?, phone = ?, location = ?, current_employer = ?, current_title = ?,
                total_experience_years = ?, notice_period_days = ?, highest_qualification = ?, skills = ?,
                summary = ?, version = version + 1, updated_at = now()
            WHERE id = ? AND deleted_at IS NULL AND version = ?
            RETURNING id, full_name, email, phone, location, current_employer, current_title,
                      total_experience_years, notice_period_days, highest_qualification, skills, summary,
                      status, version, created_at, updated_at, archived_at, deleted_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.fullName());
            ps.setString(2, c.email());
            ps.setString(3, c.phone());
            ps.setString(4, c.location());
            ps.setString(5, c.currentEmployer());
            ps.setString(6, c.currentTitle());
            ps.setInt(7, c.totalExperienceYears());
            setNullableInt(ps, 8, c.noticePeriodDays());
            ps.setString(9, c.highestQualification().name());
            ps.setArray(10, toSqlArray(conn, c.skills()));
            ps.setString(11, c.summary());
            ps.setObject(12, id);
            ps.setInt(13, expectedVersion);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update candidate", e);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.SMALLINT);
        } else {
            ps.setInt(index, value);
        }
    }

    private static Array toSqlArray(Connection conn, List<String> skills) throws SQLException {
        return conn.createArrayOf("text", skills.toArray(new String[0]));
    }

    private static Candidate map(ResultSet rs) throws SQLException {
        String[] skillsArray = (String[]) rs.getArray("skills").getArray();
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        return new Candidate(
            (UUID) rs.getObject("id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("location"),
            rs.getString("current_employer"),
            rs.getString("current_title"),
            rs.getInt("total_experience_years"),
            (Integer) rs.getObject("notice_period_days"),
            HighestQualification.valueOf(rs.getString("highest_qualification")),
            List.of(skillsArray),
            rs.getString("summary"),
            CandidateStatus.valueOf(rs.getString("status")),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            archivedAt == null ? null : archivedAt.toInstant(),
            deletedAt == null ? null : deletedAt.toInstant()
        );
    }
}

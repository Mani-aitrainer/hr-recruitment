package com.hr.recruitment.candidate;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.hr.recruitment.common.ApiExceptions.MalformedRequestException;
import com.hr.recruitment.common.ApiExceptions.ValidationException;
import com.hr.recruitment.common.Router.RouteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates request parsing and bean-validation boundaries using sample Function URL v2
 * events. Business-rule tests live in CandidateServiceTest; these are the negative
 * "malformed JSON" and "each field rule, both boundaries" cases from the test rule.
 */
@ExtendWith(MockitoExtension.class)
class CandidateControllerTest {

    @Mock
    private CandidateService service;

    private CandidateController controllerWith(CandidateService svc) {
        return new CandidateController(svc);
    }

    private APIGatewayV2HTTPEvent eventWithBody(String body) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        return event;
    }

    private String validJson() {
        return """
            {
              "fullName": "Asha Rao",
              "email": "asha.rao@example.com",
              "phone": "9876543210",
              "location": "Bengaluru",
              "totalExperienceYears": 5,
              "highestQualification": "BACHELORS",
              "skills": ["Java"]
            }
            """;
    }

    // ---- negative: malformed JSON -> 400 ----
    @Test
    void create_rejects_malformed_json() {
        CandidateController controller = controllerWith(service);
        APIGatewayV2HTTPEvent event = eventWithBody("{ not valid json");

        assertThatThrownBy(() -> controller.create(event, Map.of()))
            .isInstanceOf(MalformedRequestException.class);
    }

    @Test
    void create_rejects_empty_body() {
        CandidateController controller = controllerWith(service);
        APIGatewayV2HTTPEvent event = eventWithBody("");

        assertThatThrownBy(() -> controller.create(event, Map.of()))
            .isInstanceOf(MalformedRequestException.class);
    }

    // ---- CAND-2: negative, fullName boundary ----
    @Test
    void create_rejects_fullName_of_one_char() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"Asha Rao\"", "\"A\"");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("fullName")));
    }

    @Test
    void create_accepts_fullName_of_two_chars() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"Asha Rao\"", "\"Aa\"");
        // Should pass validation and reach the service (which is mocked, returns null -> NPE
        // in CandidateResponse.from is avoided by stubbing).
        org.mockito.Mockito.when(service.create(org.mockito.ArgumentMatchers.any()))
            .thenReturn(sampleCandidate());

        RouteResult result = controller.create(eventWithBody(body), Map.of());

        assertThat(result.status()).isEqualTo(201);
    }

    // ---- CAND-3: negative, email format ----
    @Test
    void create_rejects_malformed_email() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("asha.rao@example.com", "mani@");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("email")));
    }

    /**
     * "a@b" has no dot in the domain. Jakarta's default @Email accepts it, but the DB CHECK
     * (db/migrations/V1__create_candidate.sql) requires a dot, so this must fail validation
     * here — otherwise it reaches the database and the CHECK turns it into a 500, not a 400.
     */
    @Test
    void create_rejects_email_with_no_dot_in_domain() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("asha.rao@example.com", "a@b");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("email")));
    }

    /**
     * " a" is 2 raw characters, so @Size(min=2) alone would accept it — but CandidateService
     * trims before storing, and the trimmed value "a" (1 char) would fail the DB CHECK with
     * a 500. Json's trimming deserializer must trim before bean validation runs.
     */
    @Test
    void create_rejects_fullName_that_is_only_two_chars_before_trimming() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"Asha Rao\"", "\" a\"");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("fullName")));
    }

    // ---- CAND-4: negative, totalExperienceYears boundary ----
    @Test
    void create_rejects_experience_of_41() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"totalExperienceYears\": 5", "\"totalExperienceYears\": 41");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("totalExperienceYears")));
    }

    @Test
    void create_accepts_experience_of_40() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"totalExperienceYears\": 5", "\"totalExperienceYears\": 40");
        org.mockito.Mockito.when(service.create(org.mockito.ArgumentMatchers.any()))
            .thenReturn(sampleCandidate());

        RouteResult result = controller.create(eventWithBody(body), Map.of());

        assertThat(result.status()).isEqualTo(201);
    }

    @Test
    void create_rejects_experience_of_negative_one() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"totalExperienceYears\": 5", "\"totalExperienceYears\": -1");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class);
    }

    // ---- CAND-5: negative, skills cardinality boundary ----
    @Test
    void create_rejects_empty_skills() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("[\"Java\"]", "[]");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("skills")));
    }

    @Test
    void create_rejects_16_skills() {
        CandidateController controller = controllerWith(service);
        String sixteen = "[\"s1\",\"s2\",\"s3\",\"s4\",\"s5\",\"s6\",\"s7\",\"s8\",\"s9\",\"s10\",\"s11\",\"s12\",\"s13\",\"s14\",\"s15\",\"s16\"]";
        String body = validJson().replace("[\"Java\"]", sixteen);

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class);
    }

    // ---- required fields ----
    @Test
    void create_rejects_missing_highestQualification() {
        CandidateController controller = controllerWith(service);
        String body = validJson().replace("\"highestQualification\": \"BACHELORS\",", "");

        assertThatThrownBy(() -> controller.create(eventWithBody(body), Map.of()))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).errors())
                .anyMatch(err -> err.field().equals("highestQualification")));
    }

    // ---- update requires version ----
    @Test
    void update_rejects_body_without_version() {
        CandidateController controller = controllerWith(service);
        String body = validJson();
        java.util.UUID id = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(service.update(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new ValidationException(java.util.List.of(
                new com.hr.recruitment.common.ProblemDetail.FieldError("version", "is required"))));

        assertThatThrownBy(() -> controller.update(eventWithBody(body), Map.of("id", id.toString())))
            .isInstanceOf(ValidationException.class);
    }

    private com.hr.recruitment.candidate.model.Candidate sampleCandidate() {
        return new com.hr.recruitment.candidate.model.Candidate(
            java.util.UUID.randomUUID(), "Asha Rao", "asha.rao@example.com", "9876543210", "Bengaluru",
            null, null, 5, null, com.hr.recruitment.candidate.model.HighestQualification.BACHELORS,
            java.util.List.of("Java"), null, com.hr.recruitment.candidate.model.CandidateStatus.ACTIVE, 0,
            java.time.Instant.now(), java.time.Instant.now(), null, null);
    }
}

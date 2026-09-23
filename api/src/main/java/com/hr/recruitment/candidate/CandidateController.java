package com.hr.recruitment.candidate;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.hr.recruitment.candidate.dto.CandidateRequest;
import com.hr.recruitment.candidate.dto.CandidateResponse;
import com.hr.recruitment.candidate.model.Candidate;
import com.hr.recruitment.common.ApiExceptions.MalformedRequestException;
import com.hr.recruitment.common.ApiExceptions.ValidationException;
import com.hr.recruitment.common.Json;
import com.hr.recruitment.common.ProblemDetail;
import com.hr.recruitment.common.Router.RouteResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP <-> DTO mapping for /api/v1/candidates. No business rules here — those live in
 * CandidateService. Path params are supplied by the Router, never parsed here.
 */
public final class CandidateController {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    private final CandidateService service;

    public CandidateController(CandidateService service) {
        this.service = service;
    }

    public RouteResult create(APIGatewayV2HTTPEvent event, Map<String, String> pathParams) {
        CandidateRequest request = parseAndValidate(event);
        Candidate created = service.create(request);
        return new RouteResult(201, CandidateResponse.from(created),
            Map.of("Location", "/api/v1/candidates/" + created.id()));
    }

    public RouteResult getById(APIGatewayV2HTTPEvent event, Map<String, String> pathParams) {
        UUID id = parseId(pathParams);
        Candidate found = service.getById(id);
        return RouteResult.of(200, CandidateResponse.from(found));
    }

    public RouteResult update(APIGatewayV2HTTPEvent event, Map<String, String> pathParams) {
        UUID id = parseId(pathParams);
        CandidateRequest request = parseAndValidate(event);
        Candidate updated = service.update(id, request);
        return RouteResult.of(200, CandidateResponse.from(updated));
    }

    private static UUID parseId(Map<String, String> pathParams) {
        try {
            return UUID.fromString(pathParams.get("id"));
        } catch (IllegalArgumentException e) {
            throw new com.hr.recruitment.common.ApiExceptions.NotFoundException("Candidate", pathParams.get("id"));
        }
    }

    private CandidateRequest parseAndValidate(APIGatewayV2HTTPEvent event) {
        CandidateRequest request;
        try {
            String body = event.getBody();
            if (body == null || body.isBlank()) {
                throw new MalformedRequestException("Request body is empty");
            }
            request = Json.MAPPER.readValue(body, CandidateRequest.class);
        } catch (MalformedRequestException e) {
            throw e;
        } catch (com.fasterxml.jackson.databind.exc.InvalidFormatException e) {
            // e.g. an unknown enum value for highestQualification — name the field, not the value.
            String field = e.getPath().isEmpty() ? "request" : e.getPath().get(e.getPath().size() - 1).getFieldName();
            throw new ValidationException(List.of(new ProblemDetail.FieldError(field, "has an invalid value")));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Genuinely malformed JSON (syntax error, wrong type for a field, etc).
            throw new MalformedRequestException("Request body is not valid JSON");
        }

        var violations = VALIDATOR.validate(request);
        if (!violations.isEmpty()) {
            List<ProblemDetail.FieldError> errors = violations.stream()
                .map(this::toFieldError)
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
            throw new ValidationException(errors);
        }
        return request;
    }

    private ProblemDetail.FieldError toFieldError(ConstraintViolation<CandidateRequest> violation) {
        String field = violation.getPropertyPath().toString();
        return new ProblemDetail.FieldError(field, violation.getMessage());
    }
}

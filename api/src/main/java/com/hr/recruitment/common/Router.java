package com.hr.recruitment.common;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.hr.recruitment.common.ApiExceptions.InvalidStatusTransitionException;
import com.hr.recruitment.common.ApiExceptions.MalformedRequestException;
import com.hr.recruitment.common.ApiExceptions.NotFoundException;
import com.hr.recruitment.common.ApiExceptions.ValidationException;
import com.hr.recruitment.common.ApiExceptions.VersionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal path router for Lambda Function URL (payload format 2.0) requests.
 * Path params are parsed here and only here — controllers never touch raw paths.
 */
public final class Router {

    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    /** A registered route handler. Receives the parsed path params and the raw event. */
    public interface RouteHandler {
        RouteResult handle(APIGatewayV2HTTPEvent event, Map<String, String> pathParams, String requestId) throws Exception;
    }

    public record RouteResult(int status, Object body, Map<String, String> headers) {
        public static RouteResult of(int status, Object body) {
            return new RouteResult(status, body, Map.of());
        }
    }

    private record Route(String method, Pattern pattern, List<String> paramNames, RouteHandler handler) {}

    private final List<Route> routes = new ArrayList<>();

    public Router register(String method, String pathTemplate, RouteHandler handler) {
        List<String> paramNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        for (String segment : pathTemplate.split("/")) {
            if (segment.isEmpty()) continue;
            regex.append('/');
            if (segment.startsWith("{") && segment.endsWith("}")) {
                paramNames.add(segment.substring(1, segment.length() - 1));
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        if (regex.length() == 1) regex.append('/');
        regex.append("/?$");
        routes.add(new Route(method.toUpperCase(), Pattern.compile(regex.toString()), paramNames, handler));
        return this;
    }

    public APIGatewayV2HTTPResponse dispatch(APIGatewayV2HTTPEvent event, String requestId) {
        long start = System.currentTimeMillis();
        String method = event.getRequestContext() != null && event.getRequestContext().getHttp() != null
            ? event.getRequestContext().getHttp().getMethod()
            : "UNKNOWN";
        String path = normalizePath(event.getRawPath());

        RouteResult result;
        int status;
        try {
            result = route(event, method, path, requestId);
            status = result.status();
        } catch (ValidationException e) {
            result = new RouteResult(400, ProblemDetail.validation(path, requestId, e.errors()), Map.of());
            status = 400;
        } catch (MalformedRequestException e) {
            result = new RouteResult(400, ProblemDetail.of(ProblemDetail.TYPE_MALFORMED_REQUEST,
                "Malformed request", 400, "The request body could not be parsed", path, requestId), Map.of());
            status = 400;
        } catch (NotFoundException e) {
            result = new RouteResult(404, ProblemDetail.of(ProblemDetail.TYPE_NOT_FOUND,
                "Not found", 404, "The requested resource does not exist", path, requestId), Map.of());
            status = 404;
        } catch (InvalidStatusTransitionException e) {
            result = new RouteResult(409, ProblemDetail.of(ProblemDetail.TYPE_INVALID_TRANSITION,
                "Invalid status transition", 409, e.getMessage(), path, requestId), Map.of());
            status = 409;
        } catch (VersionConflictException e) {
            result = new RouteResult(409, ProblemDetail.of(ProblemDetail.TYPE_VERSION_CONFLICT,
                "Version conflict", 409, "The resource was modified by another request", path, requestId), Map.of());
            status = 409;
        } catch (NoRouteException e) {
            result = new RouteResult(404, ProblemDetail.of(ProblemDetail.TYPE_NOT_FOUND,
                "Not found", 404, "No route matches " + method + " " + path, path, requestId), Map.of());
            status = 404;
        } catch (Exception e) {
            // Never log e.getMessage() here — it may embed request data. Log only the type.
            LOG.error("unhandled exception type={}", e.getClass().getSimpleName());
            result = new RouteResult(500, ProblemDetail.of("https://hr-recruitment/errors/internal",
                "Internal error", 500, "An unexpected error occurred", path, requestId), Map.of());
            status = 500;
        }

        RequestLog.log(requestId, method, path, status, System.currentTimeMillis() - start);
        return toResponse(result);
    }

    private RouteResult route(APIGatewayV2HTTPEvent event, String method, String path, String requestId) throws Exception {
        for (Route route : routes) {
            if (!route.method().equals(method)) continue;
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) continue;
            Map<String, String> params = new java.util.HashMap<>();
            for (int i = 0; i < route.paramNames().size(); i++) {
                params.put(route.paramNames().get(i), matcher.group(i + 1));
            }
            return route.handler().handle(event, params, requestId);
        }
        throw new NoRouteException();
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "/";
        return rawPath.length() > 1 && rawPath.endsWith("/") ? rawPath.substring(0, rawPath.length() - 1) : rawPath;
    }

    private APIGatewayV2HTTPResponse toResponse(RouteResult result) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(result.status());
        Map<String, String> headers = new java.util.HashMap<>(result.headers());
        headers.putIfAbsent("Content-Type", isProblem(result) ? "application/problem+json" : "application/json");
        response.setHeaders(headers);
        try {
            response.setBody(result.body() == null ? "" : Json.MAPPER.writeValueAsString(result.body()));
        } catch (Exception e) {
            response.setStatusCode(500);
            response.setBody("{\"detail\":\"Failed to serialize response\"}");
        }
        return response;
    }

    private static boolean isProblem(RouteResult result) {
        return result.body() instanceof ProblemDetail;
    }

    private static final class NoRouteException extends RuntimeException {}
}

package com.hr.recruitment.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * One structured JSON log line per request: requestId, method, path, status, duration.
 * Never logs the request body, query values, or any field value (candidate spec,
 * non-functional requirements: no PII in logs).
 */
public final class RequestLog {

    private static final Logger LOG = LoggerFactory.getLogger("request");

    private RequestLog() {}

    public static void log(String requestId, String method, String path, int status, long durationMs) {
        try {
            MDC.put("requestId", requestId);
            MDC.put("method", method);
            MDC.put("path", path);
            MDC.put("status", String.valueOf(status));
            MDC.put("durationMs", String.valueOf(durationMs));
            LOG.info("request completed");
        } finally {
            MDC.clear();
        }
    }
}

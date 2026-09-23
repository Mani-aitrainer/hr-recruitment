package com.hr.recruitment.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

/** Shared, immutable Jackson mapper. One instance per Lambda container. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(trimmingStringModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
        .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private Json() {}

    /**
     * Every String field is trimmed as it deserializes, so bean-validation length rules
     * (e.g. fullName 2-100 chars) are checked against the same value the service stores.
     * Without this, " a" (2 raw chars) passes @Size(min=2) but is stored as "a" after
     * CandidateService trims it, and the DB CHECK then rejects it with a 500, not a 400.
     */
    private static SimpleModule trimmingStringModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new StringDeserializer() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String value = super.deserialize(p, ctxt);
                return value == null ? null : value.trim();
            }
        });
        return module;
    }
}

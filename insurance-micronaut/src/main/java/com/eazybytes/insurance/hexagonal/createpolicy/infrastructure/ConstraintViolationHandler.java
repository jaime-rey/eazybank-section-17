package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.validation.exceptions.ConstraintExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;

import java.util.HashMap;
import java.util.Map;

@Produces
@Singleton
@Replaces(ConstraintExceptionHandler.class)
public class ConstraintViolationHandler
    implements ExceptionHandler<ConstraintViolationException, HttpResponse<Map<String, String>>> {

    @Override
    public HttpResponse<Map<String, String>> handle(HttpRequest request, ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = extractLastNode(violation.getPropertyPath().toString());
            errors.put(field, violation.getMessage());
        });
        return HttpResponse.badRequest(errors);
    }

    private String extractLastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}

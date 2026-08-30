package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.PolicyValidationException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.Map;

@Produces
@Singleton
@Requires(classes = {PolicyValidationException.class, ExceptionHandler.class})
public class PolicyValidationExceptionHandler
    implements ExceptionHandler<PolicyValidationException, HttpResponse<Map<String, String>>> {

    @Override
    public HttpResponse<Map<String, String>> handle(HttpRequest request, PolicyValidationException ex) {
        return HttpResponse.badRequest(Map.of("error", ex.getMessage()));
    }
}

package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CreatePolicyUseCase;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.validation.Valid;

import java.time.LocalDate;

@Controller("/api/policies")
public class PolicyController {

    private final CreatePolicyUseCase createPolicyUseCase;

    public PolicyController(CreatePolicyUseCase createPolicyUseCase) {
        this.createPolicyUseCase = createPolicyUseCase;
    }

    @Post
    public HttpResponse<Policy> create(@Valid @Body CreatePolicyRequest request) {
        Policy policy = createPolicyUseCase.execute(request.toCommand(), LocalDate.now());
        return HttpResponse.created(policy);
    }
}

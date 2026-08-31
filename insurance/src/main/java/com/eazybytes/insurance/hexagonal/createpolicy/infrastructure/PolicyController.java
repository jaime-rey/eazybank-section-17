package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.CreatePolicyUseCase;
import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final CreatePolicyUseCase createPolicyUseCase;

    @PostMapping
    public ResponseEntity<Policy>  create(@Valid @RequestBody CreatePolicyRequest request){
        Policy policy = createPolicyUseCase.execute(request.toCommand(), LocalDate.now(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(policy);
    }

}

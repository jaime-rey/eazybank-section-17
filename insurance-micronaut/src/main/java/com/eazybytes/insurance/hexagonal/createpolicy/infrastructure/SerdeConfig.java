package com.eazybytes.insurance.hexagonal.createpolicy.infrastructure;

import com.eazybytes.insurance.hexagonal.createpolicy.domain.Policy;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(Policy.class)
public class SerdeConfig {
}

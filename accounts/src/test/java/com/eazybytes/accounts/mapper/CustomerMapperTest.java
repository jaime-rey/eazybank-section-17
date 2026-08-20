package com.eazybytes.accounts.mapper;

import com.eazybytes.accounts.dto.CustomerDetailsDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    private final CustomerMapper mapper = Mappers.getMapper(CustomerMapper.class);

    @Test
    @DisplayName("toDto copies name, email and mobileNumber to a new CustomerDto")
    void toDto_copiesFields() {
        Customer source = new Customer();
        source.setName("Ada Lovelace");
        source.setEmail("ada@example.com");
        source.setMobileNumber("9345432123");

        CustomerDto result = mapper.toDto(source);

        assertThat(result.getName()).isEqualTo("Ada Lovelace");
        assertThat(result.getEmail()).isEqualTo("ada@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
    }

    @Test
    @DisplayName("toDetailsDto copies name, email and mobileNumber to a new CustomerDetailsDto")
    void toDetailsDto_copiesFields() {
        Customer source = new Customer();
        source.setName("Grace Hopper");
        source.setEmail("grace@example.com");
        source.setMobileNumber("9345432124");

        CustomerDetailsDto result = mapper.toDetailsDto(source);

        assertThat(result.getName()).isEqualTo("Grace Hopper");
        assertThat(result.getEmail()).isEqualTo("grace@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432124");
    }

    @Test
    @DisplayName("toEntity copies name, email and mobileNumber to a new Customer entity")
    void toEntity_copiesFields() {
        CustomerDto source = new CustomerDto();
        source.setName("Alan Turing");
        source.setEmail("alan@example.com");
        source.setMobileNumber("9345432125");

        Customer result = mapper.toEntity(source);

        assertThat(result.getName()).isEqualTo("Alan Turing");
        assertThat(result.getEmail()).isEqualTo("alan@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432125");
    }

    @Test
    @DisplayName("updateEntity copies name, email and mobileNumber into the given Customer instance")
    void updateEntity_copiesFields() {
        CustomerDto source = new CustomerDto();
        source.setName("Edsger Dijkstra");
        source.setEmail("edsger@example.com");
        source.setMobileNumber("9345432126");
        Customer target = new Customer();

        mapper.updateEntity(source, target);

        assertThat(target.getName()).isEqualTo("Edsger Dijkstra");
        assertThat(target.getEmail()).isEqualTo("edsger@example.com");
        assertThat(target.getMobileNumber()).isEqualTo("9345432126");
    }
}

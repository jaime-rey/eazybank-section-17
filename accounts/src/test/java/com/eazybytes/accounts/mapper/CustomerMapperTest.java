package com.eazybytes.accounts.mapper;

import com.eazybytes.accounts.dto.CustomerDetailsDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    @Test
    @DisplayName("mapToCustomerDto copies name, email and mobileNumber to the target CustomerDto")
    void mapToCustomerDto_copiesFields() {
        Customer source = new Customer();
        source.setName("Ada Lovelace");
        source.setEmail("ada@example.com");
        source.setMobileNumber("9345432123");
        CustomerDto target = new CustomerDto();

        CustomerDto result = CustomerMapper.mapToCustomerDto(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getName()).isEqualTo("Ada Lovelace");
        assertThat(result.getEmail()).isEqualTo("ada@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432123");
    }

    @Test
    @DisplayName("mapToCustomerDetailsDto copies name, email and mobileNumber to CustomerDetailsDto")
    void mapToCustomerDetailsDto_copiesFields() {
        Customer source = new Customer();
        source.setName("Grace Hopper");
        source.setEmail("grace@example.com");
        source.setMobileNumber("9345432124");
        CustomerDetailsDto target = new CustomerDetailsDto();

        CustomerDetailsDto result = CustomerMapper.mapToCustomerDetailsDto(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getName()).isEqualTo("Grace Hopper");
        assertThat(result.getEmail()).isEqualTo("grace@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432124");
    }

    @Test
    @DisplayName("mapToCustomer copies name, email and mobileNumber to the target Customer entity")
    void mapToCustomer_copiesFields() {
        CustomerDto source = new CustomerDto();
        source.setName("Alan Turing");
        source.setEmail("alan@example.com");
        source.setMobileNumber("9345432125");
        Customer target = new Customer();

        Customer result = CustomerMapper.mapToCustomer(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getName()).isEqualTo("Alan Turing");
        assertThat(result.getEmail()).isEqualTo("alan@example.com");
        assertThat(result.getMobileNumber()).isEqualTo("9345432125");
    }
}

package com.eazybytes.accounts.mapper;

import com.eazybytes.accounts.dto.CustomerDetailsDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerDto toDto(Customer customer);

    CustomerDetailsDto toDetailsDto(Customer customer);

    Customer toEntity(CustomerDto customerDto);

    void updateEntity(CustomerDto customerDto, @MappingTarget Customer customer);
}

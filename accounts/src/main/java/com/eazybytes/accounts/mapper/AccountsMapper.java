package com.eazybytes.accounts.mapper;

import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.entity.Accounts;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AccountsMapper {

    AccountsDto toDto(Accounts accounts);

    void updateEntity(AccountsDto accountsDto, @MappingTarget Accounts accounts);
}

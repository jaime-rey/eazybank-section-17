package com.eazybytes.accounts.mapper;

import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.entity.Accounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsMapperTest {

    private final AccountsMapper mapper = Mappers.getMapper(AccountsMapper.class);

    @Test
    @DisplayName("toDto copies fields from Accounts to a new AccountsDto")
    void toDto_copiesFields() {
        Accounts source = new Accounts();
        source.setAccountNumber(1234567890L);
        source.setAccountType("Savings");
        source.setBranchAddress("123 NY Main Street");

        AccountsDto result = mapper.toDto(source);

        assertThat(result.getAccountNumber()).isEqualTo(1234567890L);
        assertThat(result.getAccountType()).isEqualTo("Savings");
        assertThat(result.getBranchAddress()).isEqualTo("123 NY Main Street");
    }

    @Test
    @DisplayName("updateEntity copies fields from AccountsDto into the given Accounts instance")
    void updateEntity_copiesFields() {
        AccountsDto source = new AccountsDto();
        source.setAccountNumber(9876543210L);
        source.setAccountType("Checking");
        source.setBranchAddress("456 LA Central Avenue");
        Accounts target = new Accounts();

        mapper.updateEntity(source, target);

        assertThat(target.getAccountNumber()).isEqualTo(9876543210L);
        assertThat(target.getAccountType()).isEqualTo("Checking");
        assertThat(target.getBranchAddress()).isEqualTo("456 LA Central Avenue");
    }
}

package com.eazybytes.accounts.mapper;

import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.entity.Accounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsMapperTest {

    @Test
    @DisplayName("mapToAccountsDto copies fields from Accounts to AccountsDto and returns the same DTO instance")
    void mapToAccountsDto_copiesFields() {
        Accounts source = new Accounts();
        source.setAccountNumber(1234567890L);
        source.setAccountType("Savings");
        source.setBranchAddress("123 NY Main Street");
        AccountsDto target = new AccountsDto();

        AccountsDto result = AccountsMapper.mapToAccountsDto(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getAccountNumber()).isEqualTo(1234567890L);
        assertThat(result.getAccountType()).isEqualTo("Savings");
        assertThat(result.getBranchAddress()).isEqualTo("123 NY Main Street");
    }

    @Test
    @DisplayName("mapToAccounts copies fields from AccountsDto to Accounts and returns the same entity instance")
    void mapToAccounts_copiesFields() {
        AccountsDto source = new AccountsDto();
        source.setAccountNumber(9876543210L);
        source.setAccountType("Checking");
        source.setBranchAddress("456 LA Central Avenue");
        Accounts target = new Accounts();

        Accounts result = AccountsMapper.mapToAccounts(source, target);

        assertThat(result).isSameAs(target);
        assertThat(result.getAccountNumber()).isEqualTo(9876543210L);
        assertThat(result.getAccountType()).isEqualTo("Checking");
        assertThat(result.getBranchAddress()).isEqualTo("456 LA Central Avenue");
    }
}

package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.AccountResponse;
import at.ymeri.my.finance.application.data.AccountType;
import at.ymeri.my.finance.application.data.CreateAccountRequest;
import at.ymeri.my.finance.application.data.UpdateAccountRequest;
import at.ymeri.my.finance.domain.data.account.AccountDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AccountMapper {

    AccountMapper INSTANCE = Mappers.getMapper(AccountMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", qualifiedByName = "apiTypeToDomain")
    AccountDto map(CreateAccountRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", qualifiedByName = "apiTypeToDomain")
    AccountDto map(UpdateAccountRequest request);

    @Mapping(target = "type", qualifiedByName = "domainTypeToApi")
    AccountResponse map(AccountDto accountDto);

    List<AccountResponse> mapList(List<AccountDto> dtos);

    @Named("apiTypeToDomain")
    default at.ymeri.my.finance.domain.data.account.AccountType apiTypeToDomain(AccountType type) {
        return type != null
                ? at.ymeri.my.finance.domain.data.account.AccountType.valueOf(type.getValue())
                : null;
    }

    @Named("domainTypeToApi")
    default AccountType domainTypeToApi(at.ymeri.my.finance.domain.data.account.AccountType type) {
        return type != null ? AccountType.fromValue(type.name()) : null;
    }
}

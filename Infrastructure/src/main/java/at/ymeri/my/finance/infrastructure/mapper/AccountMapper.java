package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.account.AccountDto;
import at.ymeri.my.finance.domain.data.account.AccountType;
import at.ymeri.my.finance.infrastructure.entity.AccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface AccountMapper {

    AccountMapper INSTANCE = Mappers.getMapper(AccountMapper.class);

    @Mapping(target = "type", qualifiedByName = "typeToString")
    AccountEntity map(AccountDto accountDto);

    @Mapping(target = "type", qualifiedByName = "stringToType")
    AccountDto map(AccountEntity entity);

    List<AccountDto> map(List<AccountEntity> entities);

    @Named("typeToString")
    default String typeToString(AccountType type) {
        return type != null ? type.name() : null;
    }

    @Named("stringToType")
    default AccountType stringToType(String type) {
        return type != null ? AccountType.valueOf(type) : null;
    }
}

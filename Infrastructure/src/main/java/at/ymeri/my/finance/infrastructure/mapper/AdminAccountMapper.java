package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.auth.AdminAccountDto;
import at.ymeri.my.finance.infrastructure.entity.AdminAccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface AdminAccountMapper {

    AdminAccountMapper INSTANCE = Mappers.getMapper(AdminAccountMapper.class);

    AdminAccountEntity map(AdminAccountDto dto);

    AdminAccountDto map(AdminAccountEntity entity);
}

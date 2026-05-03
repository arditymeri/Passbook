package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.infrastructure.entity.CategoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(target = "type", qualifiedByName = "typeToString")
    CategoryEntity map(CategoryDto categoryDto);

    @Mapping(target = "type", qualifiedByName = "stringToType")
    CategoryDto map(CategoryEntity entity);

    List<CategoryDto> map(List<CategoryEntity> entities);

    @Named("typeToString")
    default String typeToString(CategoryType type) {
        return type != null ? type.name() : null;
    }

    @Named("stringToType")
    default CategoryType stringToType(String type) {
        return type != null ? CategoryType.valueOf(type) : null;
    }
}

package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.CategoryResponse;
import at.ymeri.my.finance.application.data.CategoryType;
import at.ymeri.my.finance.application.data.CreateCategoryRequest;
import at.ymeri.my.finance.application.data.UpdateCategoryRequest;
import at.ymeri.my.finance.domain.data.category.CategoryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", qualifiedByName = "apiTypeToDomain")
    CategoryDto map(CreateCategoryRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "type", qualifiedByName = "apiTypeToDomain")
    CategoryDto map(UpdateCategoryRequest request);

    @Mapping(target = "type", qualifiedByName = "domainTypeToApi")
    CategoryResponse map(CategoryDto categoryDto);

    List<CategoryResponse> mapList(List<CategoryDto> dtos);

    @Named("apiTypeToDomain")
    default at.ymeri.my.finance.domain.data.category.CategoryType apiTypeToDomain(CategoryType type) {
        return type != null
                ? at.ymeri.my.finance.domain.data.category.CategoryType.valueOf(type.getValue())
                : null;
    }

    @Named("domainTypeToApi")
    default CategoryType domainTypeToApi(at.ymeri.my.finance.domain.data.category.CategoryType type) {
        return type != null ? CategoryType.fromValue(type.name()) : null;
    }
}

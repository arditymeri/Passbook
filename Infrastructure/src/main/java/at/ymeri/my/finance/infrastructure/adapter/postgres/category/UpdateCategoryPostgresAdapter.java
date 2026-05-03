package at.ymeri.my.finance.infrastructure.adapter.postgres.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.UpdateCategoryPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.CategoryEntity;
import at.ymeri.my.finance.infrastructure.mapper.CategoryMapper;
import at.ymeri.my.finance.infrastructure.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateCategoryPostgresAdapter implements UpdateCategoryPersistencePort {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryPostgresAdapter(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryDto updateCategory(String id, CategoryDto categoryDto) {
        CategoryEntity entity = CategoryMapper.INSTANCE.map(categoryDto);
        entity.setId(UUID.fromString(id));
        return CategoryMapper.INSTANCE.map(categoryRepository.save(entity));
    }
}

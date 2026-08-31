package at.ymeri.my.finance.infrastructure.adapter.postgres.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.UpdateCategoryPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.CategoryEntity;
import at.ymeri.my.finance.infrastructure.mapper.CategoryMapper;
import at.ymeri.my.finance.infrastructure.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UpdateCategoryPostgresAdapter implements UpdateCategoryPersistencePort {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryPostgresAdapter(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * {@code updatedAt} is stamped "now" only when the caller didn't already set one — see
     * {@code AddAccountPostgresAdapter} for why sync's import merge preserves the source
     * device's original timestamp instead.
     */
    @Override
    public CategoryDto updateCategory(String id, CategoryDto categoryDto) {
        CategoryEntity entity = categoryRepository.findById(UUID.fromString(id)).orElseThrow();
        entity.setName(categoryDto.getName());
        entity.setType(categoryDto.getType() != null ? categoryDto.getType().name() : null);
        entity.setColor(categoryDto.getColor());
        entity.setParentCategoryId(categoryDto.getParentCategoryId());
        entity.setUpdatedAt(categoryDto.getUpdatedAt() != null ? categoryDto.getUpdatedAt() : OffsetDateTime.now());
        return CategoryMapper.INSTANCE.map(categoryRepository.save(entity));
    }
}

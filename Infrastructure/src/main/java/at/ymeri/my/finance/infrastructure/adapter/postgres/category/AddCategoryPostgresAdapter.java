package at.ymeri.my.finance.infrastructure.adapter.postgres.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.AddCategoryPersistencePort;
import at.ymeri.my.finance.infrastructure.entity.CategoryEntity;
import at.ymeri.my.finance.infrastructure.mapper.CategoryMapper;
import at.ymeri.my.finance.infrastructure.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AddCategoryPostgresAdapter implements AddCategoryPersistencePort {

    private final CategoryRepository categoryRepository;

    public AddCategoryPostgresAdapter(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * {@code updatedAt} is stamped "now" only when the caller didn't already set one — sync's
     * import merge sets it explicitly to the source device's original timestamp instead, so that
     * value is preserved (see {@code AddAccountPostgresAdapter} for the full rationale).
     */
    @Override
    public CategoryDto addCategory(CategoryDto categoryDto) {
        CategoryEntity entity = CategoryMapper.INSTANCE.map(categoryDto);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(OffsetDateTime.now());
        }
        return CategoryMapper.INSTANCE.map(categoryRepository.save(entity));
    }
}

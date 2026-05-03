package at.ymeri.my.finance.infrastructure.adapter.postgres.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.spi.category.AddCategoryPersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.CategoryMapper;
import at.ymeri.my.finance.infrastructure.repository.CategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class AddCategoryPostgresAdapter implements AddCategoryPersistencePort {

    private final CategoryRepository categoryRepository;

    public AddCategoryPostgresAdapter(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryDto addCategory(CategoryDto categoryDto) {
        return CategoryMapper.INSTANCE.map(
                categoryRepository.save(CategoryMapper.INSTANCE.map(categoryDto))
        );
    }
}

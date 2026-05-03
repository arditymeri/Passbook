package at.ymeri.my.finance.infrastructure.adapter.postgres.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import at.ymeri.my.finance.infrastructure.mapper.CategoryMapper;
import at.ymeri.my.finance.infrastructure.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GetCategoryPostgresAdapter implements GetCategoryPersistencePort {

    private final CategoryRepository categoryRepository;

    public GetCategoryPostgresAdapter(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Optional<CategoryDto> getCategoryById(String id) {
        return categoryRepository.findById(UUID.fromString(id))
                .map(CategoryMapper.INSTANCE::map);
    }

    @Override
    public List<CategoryDto> getAll() {
        return CategoryMapper.INSTANCE.map(categoryRepository.findAll());
    }

    @Override
    public List<CategoryDto> getByType(CategoryType type) {
        return CategoryMapper.INSTANCE.map(categoryRepository.findByType(type.name()));
    }

    @Override
    public boolean existsByName(String name) {
        return categoryRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String id) {
        return categoryRepository.existsByNameAndIdNot(name, UUID.fromString(id));
    }
}

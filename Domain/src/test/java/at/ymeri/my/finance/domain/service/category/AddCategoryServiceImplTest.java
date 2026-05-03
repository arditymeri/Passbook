package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.spi.category.AddCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddCategoryServiceImplTest {

    @Mock
    private AddCategoryPersistencePort addCategoryPersistencePort;

    @Mock
    private GetCategoryPersistencePort getCategoryPersistencePort;

    @InjectMocks
    private AddCategoryServiceImpl addCategoryService;

    @Test
    void addCategory_validRequest_returnsSavedCategoryWithId() {
        CategoryDto request = validCategory();
        CategoryDto saved = validCategory();
        saved.setId("generated-id");
        when(getCategoryPersistencePort.existsByName("Food")).thenReturn(false);
        when(addCategoryPersistencePort.addCategory(request)).thenReturn(saved);

        CategoryDto result = addCategoryService.addCategory(request);

        assertThat(result.getId()).isEqualTo("generated-id");
        verify(addCategoryPersistencePort).addCategory(request);
    }

    @Test
    void addCategory_nullName_throwsIllegalArgumentException() {
        CategoryDto category = validCategory();
        category.setName(null);

        assertThatThrownBy(() -> addCategoryService.addCategory(category))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required");
    }

    @Test
    void addCategory_blankName_throwsIllegalArgumentException() {
        CategoryDto category = validCategory();
        category.setName("   ");

        assertThatThrownBy(() -> addCategoryService.addCategory(category))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name must not be blank");
    }

    @Test
    void addCategory_nullType_throwsIllegalArgumentException() {
        CategoryDto category = validCategory();
        category.setType(null);

        assertThatThrownBy(() -> addCategoryService.addCategory(category))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type is required");
    }

    @Test
    void addCategory_duplicateName_throwsIllegalStateException() {
        CategoryDto category = validCategory();
        when(getCategoryPersistencePort.existsByName("Food")).thenReturn(true);

        assertThatThrownBy(() -> addCategoryService.addCategory(category))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Category name already exists: Food");
    }

    private CategoryDto validCategory() {
        CategoryDto dto = new CategoryDto();
        dto.setName("Food");
        dto.setType(CategoryType.EXPENSE);
        return dto;
    }
}

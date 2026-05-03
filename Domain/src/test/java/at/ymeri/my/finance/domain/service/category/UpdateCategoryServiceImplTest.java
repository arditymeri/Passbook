package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.UpdateCategoryPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateCategoryServiceImplTest {

    @Mock
    private UpdateCategoryPersistencePort updateCategoryPersistencePort;

    @Mock
    private GetCategoryPersistencePort getCategoryPersistencePort;

    @InjectMocks
    private UpdateCategoryServiceImpl updateCategoryService;

    @Test
    void updateCategory_validRequest_returnsUpdatedCategory() {
        CategoryDto request = validCategory();
        CategoryDto updated = validCategory();
        updated.setId("id-1");
        when(getCategoryPersistencePort.getCategoryById("id-1")).thenReturn(Optional.of(updated));
        when(getCategoryPersistencePort.existsByNameAndIdNot("Food", "id-1")).thenReturn(false);
        when(updateCategoryPersistencePort.updateCategory("id-1", request)).thenReturn(updated);

        CategoryDto result = updateCategoryService.updateCategory("id-1", request);

        assertThat(result.getId()).isEqualTo("id-1");
        verify(updateCategoryPersistencePort).updateCategory("id-1", request);
    }

    @Test
    void updateCategory_notFound_throwsNoSuchElementException() {
        when(getCategoryPersistencePort.getCategoryById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateCategoryService.updateCategory("missing", validCategory()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateCategory_blankName_throwsIllegalArgumentException() {
        CategoryDto category = validCategory();
        category.setName("  ");
        when(getCategoryPersistencePort.getCategoryById("id-1")).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> updateCategoryService.updateCategory("id-1", category))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name must not be blank");
    }

    @Test
    void updateCategory_nullType_throwsIllegalArgumentException() {
        CategoryDto category = validCategory();
        category.setType(null);
        when(getCategoryPersistencePort.getCategoryById("id-1")).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> updateCategoryService.updateCategory("id-1", category))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type is required");
    }

    @Test
    void updateCategory_nameTakenByOtherCategory_throwsIllegalStateException() {
        CategoryDto category = validCategory();
        when(getCategoryPersistencePort.getCategoryById("id-1")).thenReturn(Optional.of(category));
        when(getCategoryPersistencePort.existsByNameAndIdNot("Food", "id-1")).thenReturn(true);

        assertThatThrownBy(() -> updateCategoryService.updateCategory("id-1", category))
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

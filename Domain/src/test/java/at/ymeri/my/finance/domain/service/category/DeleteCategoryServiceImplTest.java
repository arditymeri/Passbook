package at.ymeri.my.finance.domain.service.category;

import at.ymeri.my.finance.domain.data.category.CategoryDto;
import at.ymeri.my.finance.domain.data.category.CategoryType;
import at.ymeri.my.finance.domain.spi.category.DeleteCategoryPersistencePort;
import at.ymeri.my.finance.domain.spi.category.GetCategoryPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryServiceImplTest {

    @Mock
    private DeleteCategoryPersistencePort deleteCategoryPersistencePort;

    @Mock
    private GetCategoryPersistencePort getCategoryPersistencePort;

    @InjectMocks
    private DeleteCategoryServiceImpl deleteCategoryService;

    @Test
    void deleteCategory_existsAndNotReferenced_deletesSuccessfully() {
        when(getCategoryPersistencePort.getCategoryById("id-1")).thenReturn(Optional.of(existingCategory()));
        when(deleteCategoryPersistencePort.isReferencedByTransaction("id-1")).thenReturn(false);

        assertThatNoException().isThrownBy(() -> deleteCategoryService.deleteCategory("id-1"));

        verify(deleteCategoryPersistencePort).deleteCategory("id-1");
    }

    @Test
    void deleteCategory_notFound_throwsNoSuchElementException() {
        when(getCategoryPersistencePort.getCategoryById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteCategoryService.deleteCategory("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteCategory_referencedByTransaction_throwsIllegalStateException() {
        when(getCategoryPersistencePort.getCategoryById("id-1")).thenReturn(Optional.of(existingCategory()));
        when(deleteCategoryPersistencePort.isReferencedByTransaction("id-1")).thenReturn(true);

        assertThatThrownBy(() -> deleteCategoryService.deleteCategory("id-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Category is referenced by a bill or income and cannot be deleted");
    }

    private CategoryDto existingCategory() {
        CategoryDto dto = new CategoryDto();
        dto.setId("id-1");
        dto.setName("Food");
        dto.setType(CategoryType.EXPENSE);
        return dto;
    }
}

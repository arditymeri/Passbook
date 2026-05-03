package at.ymeri.my.finance.domain.spi.category;

public interface DeleteCategoryPersistencePort {

    void deleteCategory(String id);

    boolean isReferencedByTransaction(String categoryId);
}

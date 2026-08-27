package at.ymeri.my.finance.domain.spi.goal;

public interface DeleteSavingsGoalPersistencePort {

    void delete(String id);
}

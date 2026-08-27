package at.ymeri.my.finance.domain.service.recurring;

import at.ymeri.my.finance.domain.api.GetRecurringSeriesService;
import at.ymeri.my.finance.domain.data.recurring.RecurringSeriesDto;
import at.ymeri.my.finance.domain.spi.recurring.GetRecurringSeriesPersistencePort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetRecurringSeriesServiceImpl implements GetRecurringSeriesService {

    private final GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort;

    public GetRecurringSeriesServiceImpl(GetRecurringSeriesPersistencePort getRecurringSeriesPersistencePort) {
        this.getRecurringSeriesPersistencePort = getRecurringSeriesPersistencePort;
    }

    @Override
    public List<RecurringSeriesDto> getAll() {
        return getRecurringSeriesPersistencePort.getAll();
    }
}

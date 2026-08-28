package at.ymeri.my.finance.application.mapper;

import at.ymeri.my.finance.application.data.AccountForecast;
import at.ymeri.my.finance.application.data.CashFlowForecastResponse;
import at.ymeri.my.finance.application.data.ForecastEntry;
import at.ymeri.my.finance.domain.data.forecast.AccountForecastDto;
import at.ymeri.my.finance.domain.data.forecast.CashFlowForecastResult;
import at.ymeri.my.finance.domain.data.forecast.ForecastEntryDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface CashFlowForecastMapper {

    CashFlowForecastMapper INSTANCE = Mappers.getMapper(CashFlowForecastMapper.class);

    CashFlowForecastResponse map(CashFlowForecastResult result);

    AccountForecast map(AccountForecastDto dto);

    List<AccountForecast> mapAccounts(List<AccountForecastDto> dtos);

    ForecastEntry map(ForecastEntryDto dto);

    List<ForecastEntry> mapEntries(List<ForecastEntryDto> dtos);
}

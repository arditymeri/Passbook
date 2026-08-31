package at.ymeri.my.finance.infrastructure.mapper;

import at.ymeri.my.finance.domain.data.bill.BillDto;
import at.ymeri.my.finance.domain.data.bill.NecessityTag;
import at.ymeri.my.finance.infrastructure.entity.BillEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface BillMapper {

    BillMapper INSTANCE = Mappers.getMapper(BillMapper.class);

    @Mapping(target = "necessityTag", qualifiedByName = "necessityTagToString")
    BillEntity map(BillDto billDto);

    @Mapping(target = "necessityTag", qualifiedByName = "stringToNecessityTag")
    BillDto map(BillEntity billEntity);

    List<BillDto> map(List<BillEntity> entities);

    @Named("necessityTagToString")
    default String necessityTagToString(NecessityTag necessityTag) {
        return necessityTag != null ? necessityTag.name() : null;
    }

    @Named("stringToNecessityTag")
    default NecessityTag stringToNecessityTag(String necessityTag) {
        return necessityTag != null ? NecessityTag.valueOf(necessityTag) : null;
    }
}

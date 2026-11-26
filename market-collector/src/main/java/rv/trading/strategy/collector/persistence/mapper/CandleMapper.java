package rv.trading.strategy.collector.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import rv.trading.strategy.collector.persistence.entity.CandleEntity;
import rv.trading.strategy.contracts.event.CandleEvent;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CandleMapper {

    CandleEvent toEvent(CandleEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timeframeId", ignore = true)
    @Mapping(target = "closeTime", ignore = true)
    @Mapping(target = "symbol", ignore = true)
    @Mapping(target = "timeframeCode", ignore = true)
    CandleEntity toEntity(CandleEvent event);
}

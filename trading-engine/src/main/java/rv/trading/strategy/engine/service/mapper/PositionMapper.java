package rv.trading.strategy.engine.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import rv.trading.strategy.contracts.dto.internal.PositionView;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PositionMapper {

    PositionView toView(PositionEntity position);

    List<PositionView> toViews(List<PositionEntity> positions);
}

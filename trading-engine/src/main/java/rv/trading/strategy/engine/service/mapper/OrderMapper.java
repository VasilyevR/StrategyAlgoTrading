package rv.trading.strategy.engine.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;
import rv.trading.strategy.contracts.dto.internal.OrderView;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    OrderView toView(OrderEntity order);

    default PageResponse<OrderView> toPageResponse(Page<OrderEntity> page) {
        return new PageResponse<>(
                page.getContent().stream().map(this::toView).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

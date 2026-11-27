package rv.trading.strategy.engine.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByBrokerClientIdIn(Collection<Long> brokerClientIds, Pageable pageable);

    List<OrderEntity> findByStatusIn(Collection<OrderStatus> statuses);

    boolean existsByBrokerClientIdAndInstrumentPairIdAndStatusIn(
            Long brokerClientId,
            Long instrumentPairId,
            Collection<OrderStatus> statuses
    );

    Optional<OrderEntity> findFirstByPositionIdAndOrderTypeAndStatusIn(
            Long positionId,
            OrderType orderType,
            Collection<OrderStatus> statuses
    );
}

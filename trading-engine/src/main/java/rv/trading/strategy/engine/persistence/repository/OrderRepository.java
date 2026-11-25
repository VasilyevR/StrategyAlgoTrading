package rv.trading.strategy.engine.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;

import java.util.Collection;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByBrokerClientIdIn(Collection<Long> brokerClientIds, Pageable pageable);
}

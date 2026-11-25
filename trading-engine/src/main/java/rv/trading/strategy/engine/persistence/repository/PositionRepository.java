package rv.trading.strategy.engine.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    List<PositionEntity> findByBrokerClientIdInOrderByOpenedAtDesc(Collection<Long> brokerClientIds);

    List<PositionEntity> findByBrokerClientIdInAndStatusOrderByOpenedAtDesc(
            Collection<Long> brokerClientIds,
            PositionStatus status
    );

    Optional<PositionEntity> findFirstByBrokerClientIdAndInstrumentPairIdAndStatus(
            Long brokerClientId,
            Long instrumentPairId,
            PositionStatus status
    );
}

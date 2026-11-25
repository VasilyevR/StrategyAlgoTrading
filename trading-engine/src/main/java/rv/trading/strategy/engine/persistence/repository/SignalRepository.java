package rv.trading.strategy.engine.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;

public interface SignalRepository extends JpaRepository<SignalEntity, Long> {

    Page<SignalEntity> findByAlgotradingId(long algotradingId, Pageable pageable);
}

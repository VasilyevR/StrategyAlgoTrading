package rv.trading.strategy.engine.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rv.trading.strategy.contracts.dto.internal.PositionView;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.engine.persistence.repository.PositionRepository;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.PositionMapper;

import java.util.Collection;
import java.util.List;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;

    public PositionService(PositionRepository positionRepository, PositionMapper positionMapper) {
        this.positionRepository = positionRepository;
        this.positionMapper = positionMapper;
    }

    @Transactional(readOnly = true)
    public PositionView getPosition(long id) {
        return positionRepository.findById(id)
                .map(positionMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Position", id));
    }

    @Transactional(readOnly = true)
    public List<PositionView> getPositions(Collection<Long> brokerClientIds, PositionStatus status) {
        if (status == null) {
            return positionMapper.toViews(
                    positionRepository.findByBrokerClientIdInOrderByOpenedAtDesc(brokerClientIds)
            );
        }
        return positionMapper.toViews(
                positionRepository.findByBrokerClientIdInAndStatusOrderByOpenedAtDesc(brokerClientIds, status)
        );
    }
}

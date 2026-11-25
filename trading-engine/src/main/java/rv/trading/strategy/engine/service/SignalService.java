package rv.trading.strategy.engine.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.contracts.dto.internal.SignalView;
import rv.trading.strategy.engine.persistence.repository.SignalRepository;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.SignalMapper;

@Service
public class SignalService {

    private final SignalRepository signalRepository;
    private final SignalMapper signalMapper;

    public SignalService(SignalRepository signalRepository, SignalMapper signalMapper) {
        this.signalRepository = signalRepository;
        this.signalMapper = signalMapper;
    }

    @Transactional(readOnly = true)
    public SignalView getSignal(long id) {
        return signalRepository.findById(id)
                .map(signalMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Signal", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SignalView> getSignals(long algotradingId, int page, int size) {
        return signalMapper.toPageResponse(signalRepository.findByAlgotradingId(
                algotradingId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ));
    }
}

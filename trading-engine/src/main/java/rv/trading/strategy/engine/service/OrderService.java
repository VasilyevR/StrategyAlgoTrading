package rv.trading.strategy.engine.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rv.trading.strategy.contracts.dto.internal.OrderView;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.engine.persistence.repository.OrderRepository;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.OrderMapper;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Transactional(readOnly = true)
    public OrderView getOrder(long id) {
        return orderRepository.findById(id)
                .map(orderMapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderView> getOrders(List<Long> brokerClientIds, int page, int size) {
        return orderMapper.toPageResponse(orderRepository.findByBrokerClientIdIn(
                brokerClientIds,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ));
    }
}

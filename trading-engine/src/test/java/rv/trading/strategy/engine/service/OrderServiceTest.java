package rv.trading.strategy.engine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import rv.trading.strategy.contracts.dto.internal.OrderView;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.engine.TestFixtures;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.repository.OrderRepository;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.OrderMapperImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, new OrderMapperImpl());
    }

    @Test
    void getOrderMapsRepositoryEntity() {
        OrderEntity entity = TestFixtures.order(17L);
        given(orderRepository.findById(17L)).willReturn(Optional.of(entity));

        OrderView result = orderService.getOrder(17L);

        assertThat(result.id()).isEqualTo(entity.getId());
        assertThat(result.brokerClientId()).isEqualTo(entity.getBrokerClientId());
        assertThat(result.side()).isEqualTo(entity.getSide());
        assertThat(result.orderType()).isEqualTo(entity.getOrderType());
        assertThat(result.timeInForce()).isEqualTo(entity.getTimeInForce());
        assertThat(result.status()).isEqualTo(entity.getStatus());
        assertThat(result.quantity()).isEqualByComparingTo(entity.getQuantity());
        assertThat(result.createdAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void getOrderThrowsWhenMissing() {
        given(orderRepository.findById(17L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(17L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order")
                .hasMessageContaining("17");
    }

    @Test
    void getOrdersMapsContentPaginationAndDescendingCreatedAtSort() {
        List<Long> clientIds = List.of(101L, 102L);
        OrderEntity first = TestFixtures.order(17L);
        OrderEntity second = TestFixtures.order(18L);
        PageRequest request = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        given(orderRepository.findByBrokerClientIdIn(clientIds, request))
                .willReturn(new PageImpl<>(List.of(first, second), request, 5));

        PageResponse<OrderView> result = orderService.getOrders(clientIds, 1, 2);

        assertThat(result.content()).extracting(OrderView::id).containsExactly(17L, 18L);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByBrokerClientIdIn(org.mockito.ArgumentMatchers.eq(clientIds), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }
}

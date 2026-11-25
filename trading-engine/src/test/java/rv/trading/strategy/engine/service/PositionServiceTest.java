package rv.trading.strategy.engine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rv.trading.strategy.contracts.dto.internal.PositionView;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.engine.TestFixtures;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.repository.PositionRepository;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.PositionMapperImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private PositionRepository positionRepository;

    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionService(positionRepository, new PositionMapperImpl());
    }

    @Test
    void getPositionMapsRepositoryEntity() {
        PositionEntity entity = TestFixtures.position(27L);
        given(positionRepository.findById(27L)).willReturn(Optional.of(entity));

        PositionView result = positionService.getPosition(27L);

        assertThat(result.id()).isEqualTo(entity.getId());
        assertThat(result.brokerClientId()).isEqualTo(entity.getBrokerClientId());
        assertThat(result.side()).isEqualTo(entity.getSide());
        assertThat(result.status()).isEqualTo(entity.getStatus());
        assertThat(result.quantity()).isEqualByComparingTo(entity.getQuantity());
        assertThat(result.realizedPnl()).isEqualByComparingTo(entity.getRealizedPnl());
        assertThat(result.openedAt()).isEqualTo(entity.getOpenedAt());
    }

    @Test
    void getPositionThrowsWhenMissing() {
        given(positionRepository.findById(27L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> positionService.getPosition(27L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Position")
                .hasMessageContaining("27");
    }

    @Test
    void getPositionsWithoutStatusUsesUnfilteredDescendingRepositoryQuery() {
        List<Long> clientIds = List.of(101L, 102L);
        List<PositionEntity> entities = List.of(TestFixtures.position(27L), TestFixtures.position(28L));
        given(positionRepository.findByBrokerClientIdInOrderByOpenedAtDesc(clientIds)).willReturn(entities);

        List<PositionView> result = positionService.getPositions(clientIds, null);

        assertThat(result).extracting(PositionView::id).containsExactly(27L, 28L);
        verify(positionRepository).findByBrokerClientIdInOrderByOpenedAtDesc(clientIds);
    }

    @Test
    void getPositionsWithOpenStatusUsesStatusQuery() {
        assertStatusQuery(PositionStatus.OPEN);
    }

    @Test
    void getPositionsWithClosedStatusUsesStatusQuery() {
        assertStatusQuery(PositionStatus.CLOSED);
    }

    private void assertStatusQuery(PositionStatus status) {
        List<Long> clientIds = List.of(101L);
        List<PositionEntity> entities = List.of(TestFixtures.position(29L));
        given(positionRepository.findByBrokerClientIdInAndStatusOrderByOpenedAtDesc(clientIds, status))
                .willReturn(entities);

        List<PositionView> result = positionService.getPositions(clientIds, status);

        assertThat(result).extracting(PositionView::id).containsExactly(29L);
        verify(positionRepository).findByBrokerClientIdInAndStatusOrderByOpenedAtDesc(clientIds, status);
    }
}

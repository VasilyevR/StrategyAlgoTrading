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
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.contracts.dto.internal.SignalView;
import rv.trading.strategy.engine.TestFixtures;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;
import rv.trading.strategy.engine.persistence.repository.SignalRepository;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.SignalMapperImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignalServiceTest {

    @Mock
    private SignalRepository signalRepository;

    private SignalService signalService;

    @BeforeEach
    void setUp() {
        signalService = new SignalService(signalRepository, new SignalMapperImpl());
    }

    @Test
    void getSignalMapsRepositoryEntity() {
        SignalEntity entity = TestFixtures.signal(37L);
        given(signalRepository.findById(37L)).willReturn(Optional.of(entity));

        SignalView result = signalService.getSignal(37L);

        assertThat(result.id()).isEqualTo(entity.getId());
        assertThat(result.algotradingId()).isEqualTo(entity.getAlgotradingId());
        assertThat(result.signalType()).isEqualTo(entity.getSignalType());
        assertThat(result.status()).isEqualTo(entity.getStatus());
        assertThat(result.triggerPrice()).isEqualByComparingTo(entity.getTriggerPrice());
        assertThat(result.indicatorSnapshot()).isEqualTo(entity.getIndicatorSnapshot());
        assertThat(result.createdAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void getSignalThrowsWhenMissing() {
        given(signalRepository.findById(37L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> signalService.getSignal(37L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Signal")
                .hasMessageContaining("37");
    }

    @Test
    void getSignalsMapsContentPaginationAndDescendingCreatedAtSort() {
        SignalEntity first = TestFixtures.signal(37L);
        SignalEntity second = TestFixtures.signal(38L);
        PageRequest request = PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        given(signalRepository.findByAlgotradingId(301L, request))
                .willReturn(new PageImpl<>(List.of(first, second), request, 7));

        PageResponse<SignalView> result = signalService.getSignals(301L, 2, 2);

        assertThat(result.content()).extracting(SignalView::id).containsExactly(37L, 38L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(7);
        assertThat(result.totalPages()).isEqualTo(4);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(signalRepository).findByAlgotradingId(org.mockito.ArgumentMatchers.eq(301L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }
}

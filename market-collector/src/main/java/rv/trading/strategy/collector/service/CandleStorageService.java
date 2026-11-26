package rv.trading.strategy.collector.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rv.trading.strategy.collector.persistence.entity.CandleEntity;
import rv.trading.strategy.collector.persistence.mapper.CandleMapper;
import rv.trading.strategy.collector.persistence.repository.CandleRepository;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.enums.TimeframeDurations;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.collector.feed.MarketSubscriptionPort;

import java.time.Instant;
import java.util.List;

@Service
public class CandleStorageService {

    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 1000;

    private final CandleRepository candleRepository;
    private final CandleMapper candleMapper;
    private final MarketSubscriptionPort marketSubscriptionPort;

    public CandleStorageService(CandleRepository candleRepository,
                                CandleMapper candleMapper,
                                MarketSubscriptionPort marketSubscriptionPort) {
        this.candleRepository = candleRepository;
        this.candleMapper = candleMapper;
        this.marketSubscriptionPort = marketSubscriptionPort;
    }

    @Transactional
    public CandleEvent upsertCandle(CandleEvent event) {
        if (event == null || event.timeframeCode() == null) {
            return event;
        }
        Long timeframeId = candleRepository.findTimeframeIdByCode(event.timeframeCode().name()).orElse(null);
        if (timeframeId == null) {
            return event;
        }

        Instant closeTime = event.closeTime();
        if (closeTime == null && event.openTime() != null) {
            closeTime = event.openTime().plus(TimeframeDurations.duration(event.timeframeCode()));
        }

        CandleEntity entity = candleRepository
                .findByInstrumentPairIdAndTimeframeCodeAndOpenTime(
                        event.instrumentPairId(), event.timeframeCode().name(), event.openTime())
                .orElseGet(() -> candleMapper.toEntity(event));
        entity.setInstrumentPairId(event.instrumentPairId());
        entity.setTimeframeId(timeframeId);
        entity.setTimeframeCode(event.timeframeCode());
        entity.setOpenTime(event.openTime());
        entity.setCloseTime(closeTime);
        entity.setOpen(event.open());
        entity.setHigh(event.high());
        entity.setLow(event.low());
        entity.setClose(event.close());
        entity.setVolume(event.volume());
        entity.setClosed(event.closed());
        entity.setSymbol(event.symbol());

        CandleEntity saved = candleRepository.save(entity);
        saved.setTimeframeCode(event.timeframeCode());
        saved.setCloseTime(closeTime);
        saved.setSymbol(event.symbol());
        return candleMapper.toEvent(saved);
    }

    @Transactional(readOnly = true)
    public List<CandleEvent> loadRecentCandles(long instrumentPairId, TimeframeCode timeframeCode, int limit) {
        if (timeframeCode == null) {
            return List.of();
        }
        int effectiveLimit = clampLimit(limit);
        String symbol = marketSubscriptionPort.loadSubscriptions().stream()
                .filter(subscription -> subscription.instrumentPairId() == instrumentPairId)
                .findFirst()
                .map(subscription -> subscription.symbol())
                .orElse(null);
        return candleRepository.findLatestByInstrumentPairIdAndTimeframeCode(
                        instrumentPairId, timeframeCode.name(), effectiveLimit)
                .stream()
                .map(entity -> {
                    entity.setSymbol(symbol);
                    entity.setTimeframeCode(timeframeCode);
                    Instant closeTime = entity.getOpenTime() == null
                            ? null
                            : entity.getOpenTime().plus(TimeframeDurations.duration(timeframeCode));
                    entity.setCloseTime(closeTime);
                    return candleMapper.toEvent(entity);
                })
                .toList();
    }

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

}

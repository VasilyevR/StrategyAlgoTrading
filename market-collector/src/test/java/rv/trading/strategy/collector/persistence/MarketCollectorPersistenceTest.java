package rv.trading.strategy.collector.persistence;

import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import rv.trading.strategy.collector.MarketCollectorApplication;
import rv.trading.strategy.collector.feed.MarketSubscriptionPort;
import rv.trading.strategy.collector.service.CandleStorageService;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MarketCollectorApplication.class,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.jpa.hibernate.ddl-auto=validate",
                "market-collector.subscriptions[0].instrument-pair-id=1",
                "market-collector.subscriptions[0].broker-code=IBKR",
                "market-collector.subscriptions[0].symbol=BTCUSDT",
                "market-collector.subscriptions[0].market-category=linear",
                "market-collector.subscriptions[0].timeframes[0]=M1"
        })
@Transactional
class MarketCollectorPersistenceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CandleStorageService candleStorageService;

    @Autowired
    private MarketSubscriptionPort marketSubscriptionPort;

    @Autowired
    private EntityManager entityManager;

    private long instrumentPairId;

    @org.springframework.test.context.DynamicPropertySource
    static void databaseProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void createInstrumentPairFixture() {
        Long brokerId = jdbcTemplate.queryForObject(
                "SELECT id FROM broker WHERE code = ?", Long.class, "BYBIT");
        long baseInstrumentId = insertAndReturnId(
                "INSERT INTO instrument (symbol, name, asset_type) VALUES (?, ?, ?) RETURNING id",
                uniqueSymbol("B"), "Integration test base", "CRYPTO");
        long quoteInstrumentId = insertAndReturnId(
                "INSERT INTO instrument (symbol, name, asset_type) VALUES (?, ?, ?) RETURNING id",
                uniqueSymbol("Q"), "Integration test quote", "FIAT");
        instrumentPairId = insertAndReturnId(
                "INSERT INTO instrument_pair (broker_id, base_instrument_id, quote_instrument_id, symbol, "
                        + "market_type, tick_size, step_size) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                brokerId, baseInstrumentId, quoteInstrumentId, uniqueSymbol("P"), "SPOT",
                new BigDecimal("0.01000000"), new BigDecimal("0.00100000"));
    }

    @Test
    void validatesPortalSchemaAndUpsertsThenLoadsLatestCandlesChronologically() {
        assertThat(entityManager.getMetamodel().getEntities())
                .extracting(entity -> entity.getJavaType().getSimpleName())
                .contains("CandleEntity");
        assertThat(marketSubscriptionPort.loadSubscriptions())
                .anySatisfy(subscription -> {
                    assertThat(subscription.instrumentPairId()).isEqualTo(instrumentPairId);
                    assertThat(subscription.symbol()).isEqualTo("BTCUSDT");
                });

        Instant earliestOpenTime = Instant.parse("2025-01-01T10:00:00Z");
        Instant middleOpenTime = earliestOpenTime.plusSeconds(60);
        Instant latestOpenTime = middleOpenTime.plusSeconds(60);
        candleStorageService.upsertCandle(candle(earliestOpenTime, "10", true));
        candleStorageService.upsertCandle(candle(middleOpenTime, "20", true));
        candleStorageService.upsertCandle(candle(latestOpenTime, "30", true));
        candleStorageService.upsertCandle(candle(middleOpenTime, "25", false));
        entityManager.flush();
        entityManager.clear();

        List<CandleEvent> recentCandles = candleStorageService.loadRecentCandles(
                instrumentPairId, TimeframeCode.M1, 2);

        assertThat(recentCandles).hasSize(2);
        assertThat(recentCandles).extracting(CandleEvent::openTime)
                .containsExactly(middleOpenTime, latestOpenTime);
        assertThat(recentCandles).extracting(CandleEvent::symbol)
                .containsExactly("BTCUSDT", "BTCUSDT");
        assertThat(recentCandles.getFirst().closeTime()).isEqualTo(middleOpenTime.plusSeconds(60));
        assertThat(recentCandles.getFirst().open()).isEqualTo(new BigDecimal("25.00000000"));
        assertThat(recentCandles.getFirst().high()).isEqualTo(new BigDecimal("27.00000000"));
        assertThat(recentCandles.getFirst().low()).isEqualTo(new BigDecimal("24.00000000"));
        assertThat(recentCandles.getFirst().close()).isEqualTo(new BigDecimal("26.00000000"));
        assertThat(recentCandles.getFirst().volume()).isEqualTo(new BigDecimal("125.00000000"));
        assertThat(recentCandles.getFirst().closed()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candle WHERE instrument_pair_id = ? AND timeframe_id = "
                        + "(SELECT id FROM timeframe WHERE code = ?) AND open_time = ?",
                Integer.class, instrumentPairId, TimeframeCode.M1.name(), Timestamp.from(middleOpenTime)))
                .isEqualTo(1);
    }

    private CandleEvent candle(Instant openTime, String basePrice, boolean closed) {
        BigDecimal open = new BigDecimal(basePrice + ".00000000");
        return Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), instrumentPairId)
                .set(Select.field(CandleEvent.class, "symbol"), "BTCUSDT")
                .set(Select.field(CandleEvent.class, "timeframeCode"), TimeframeCode.M1)
                .set(Select.field(CandleEvent.class, "openTime"), openTime)
                .set(Select.field(CandleEvent.class, "closeTime"), openTime.plusSeconds(60))
                .set(Select.field(CandleEvent.class, "open"), open)
                .set(Select.field(CandleEvent.class, "high"), open.add(new BigDecimal("2.00000000")))
                .set(Select.field(CandleEvent.class, "low"), open.subtract(BigDecimal.ONE))
                .set(Select.field(CandleEvent.class, "close"), open.add(BigDecimal.ONE))
                .set(Select.field(CandleEvent.class, "volume"), new BigDecimal("125.00000000"))
                .set(Select.field(CandleEvent.class, "closed"), closed)
                .create();
    }

    private long insertAndReturnId(String sql, Object... arguments) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(sql, Long.class, arguments));
    }

    private static String uniqueSymbol(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}

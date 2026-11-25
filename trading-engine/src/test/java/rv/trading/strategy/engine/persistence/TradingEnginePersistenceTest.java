package rv.trading.strategy.engine.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.instancio.Instancio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.contracts.enums.TimeInForce;
import rv.trading.strategy.engine.TradeEngineApplication;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;
import rv.trading.strategy.engine.persistence.repository.OrderRepository;
import rv.trading.strategy.engine.persistence.repository.PositionRepository;
import rv.trading.strategy.engine.persistence.repository.SignalRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = TradeEngineApplication.class,
        properties = {
                "spring.flyway.locations=filesystem:../portal-api/src/main/resources/db/migration",
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@Transactional
class TradingEnginePersistenceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IndicatorRegistry indicatorRegistry;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private long brokerClientId;
    private long instrumentPairId;
    private long algotradingId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void insertForeignKeyFixtures() {
        long userId = insertAndReturnId(
                "INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id",
                UUID.randomUUID() + "@example.test",
                "test-password-hash"
        );
        long brokerId = Objects.requireNonNull(
                jdbcTemplate.queryForObject("SELECT id FROM broker WHERE code = ?", Long.class, "BYBIT")
        );
        brokerClientId = insertAndReturnId(
                "INSERT INTO broker_client (user_id, broker_id, label, encrypted_api_key, encrypted_api_secret) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id",
                userId,
                brokerId,
                "test-account",
                "encrypted-key",
                "encrypted-secret"
        );
        long baseInstrumentId = insertAndReturnId(
                "INSERT INTO instrument (symbol, name, asset_type) VALUES (?, ?, ?) RETURNING id",
                "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Test base asset",
                "CRYPTO"
        );
        long quoteInstrumentId = insertAndReturnId(
                "INSERT INTO instrument (symbol, name, asset_type) VALUES (?, ?, ?) RETURNING id",
                "Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                "Test quote asset",
                "FIAT"
        );
        instrumentPairId = insertAndReturnId(
                "INSERT INTO instrument_pair (broker_id, base_instrument_id, quote_instrument_id, symbol, " +
                        "market_type, tick_size, step_size) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                brokerId,
                baseInstrumentId,
                quoteInstrumentId,
                "PAIR-" + UUID.randomUUID().toString().substring(0, 8),
                "SPOT",
                new BigDecimal("0.01000000"),
                new BigDecimal("0.00100000")
        );
        long strategyId = insertAndReturnId(
                "INSERT INTO strategy (user_id, name) VALUES (?, ?) RETURNING id",
                userId,
                "test-strategy-" + UUID.randomUUID()
        );
        long timeframeId = Objects.requireNonNull(
                jdbcTemplate.queryForObject("SELECT id FROM timeframe WHERE code = ?", Long.class, "H1")
        );
        algotradingId = insertAndReturnId(
                "INSERT INTO algotrading (broker_client_id, strategy_id, instrument_pair_id, timeframe_id, order_volume) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id",
                brokerClientId,
                strategyId,
                instrumentPairId,
                timeframeId,
                new BigDecimal("1.00000000")
        );
    }

    @Test
    void validatesEntityMappingsAgainstPortalFlywaySchema() {
        assertThat(entityManager.getMetamodel().getEntities())
                .extracting(entity -> entity.getJavaType().getSimpleName())
                .contains("OrderEntity", "PositionEntity", "SignalEntity");
    }

    @Test
    void v3RegistersEveryIndicatorAndAddsConditionGroups() {
        List<String> indicatorCodes = jdbcTemplate.queryForList("SELECT code FROM indicator ORDER BY code", String.class);

        assertThat(indicatorCodes).containsExactlyInAnyOrder(
                java.util.Arrays.stream(IndicatorCode.values()).map(Enum::name).toArray(String[]::new));
        for (IndicatorCode code : IndicatorCode.values()) {
            assertThat(indicatorRegistry.getEvaluator(code.name()).code()).isEqualTo(code.name());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT column_default FROM information_schema.columns " +
                        "WHERE table_name = 'strategy_indicator' AND column_name = 'condition_group'", String.class))
                .contains("1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns " +
                        "WHERE table_name = 'strategy_indicator' AND column_name = 'condition_group'", String.class))
                .isEqualTo("NO");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'chk_strategy_indicator_condition_group'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM indicator_parameter p JOIN indicator i ON i.id = p.indicator_id " +
                        "WHERE i.code = 'CMF' AND p.param_key IN ('period', 'buyThreshold', 'sellThreshold')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM indicator_parameter p JOIN indicator i ON i.id = p.indicator_id " +
                        "WHERE i.code = 'AO' AND p.param_key IN ('fastPeriod', 'slowPeriod')",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void pagesOrdersAndSortsByCreatedAtDescending() {
        OrderEntity oldest = saveOrder();
        OrderEntity middle = saveOrder();
        OrderEntity newest = saveOrder();
        setOrderCreatedAt(oldest.getId(), Instant.parse("2025-01-01T00:00:00Z"));
        setOrderCreatedAt(middle.getId(), Instant.parse("2025-01-02T00:00:00Z"));
        setOrderCreatedAt(newest.getId(), Instant.parse("2025-01-03T00:00:00Z"));
        entityManager.clear();

        var firstPage = orderRepository.findByBrokerClientIdIn(
                List.of(brokerClientId),
                org.springframework.data.domain.PageRequest.of(
                        0,
                        2,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
                )
        );
        var secondPage = orderRepository.findByBrokerClientIdIn(
                List.of(brokerClientId),
                org.springframework.data.domain.PageRequest.of(
                        1,
                        2,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
                )
        );

        assertThat(firstPage.getContent()).extracting(OrderEntity::getId).containsExactly(newest.getId(), middle.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).extracting(OrderEntity::getId).containsExactly(oldest.getId());
    }

    @Test
    void findsPositionsByClientAndStatusInOpenedAtDescendingOrder() {
        PositionEntity openPosition = savePosition(PositionStatus.OPEN);
        PositionEntity closedPosition = savePosition(PositionStatus.CLOSED);
        setPositionOpenedAt(openPosition.getId(), Instant.parse("2025-01-01T00:00:00Z"));
        setPositionOpenedAt(closedPosition.getId(), Instant.parse("2025-01-02T00:00:00Z"));
        entityManager.clear();

        List<PositionEntity> all = positionRepository.findByBrokerClientIdInOrderByOpenedAtDesc(List.of(brokerClientId));
        List<PositionEntity> open = positionRepository.findByBrokerClientIdInAndStatusOrderByOpenedAtDesc(
                List.of(brokerClientId), PositionStatus.OPEN
        );
        List<PositionEntity> closed = positionRepository.findByBrokerClientIdInAndStatusOrderByOpenedAtDesc(
                List.of(brokerClientId), PositionStatus.CLOSED
        );

        assertThat(all).extracting(PositionEntity::getId).containsExactly(closedPosition.getId(), openPosition.getId());
        assertThat(open).extracting(PositionEntity::getId).containsExactly(openPosition.getId());
        assertThat(closed).extracting(PositionEntity::getId).containsExactly(closedPosition.getId());
    }

    @Test
    void persistsAndReadsSignalIndicatorSnapshotAsJsonb() {
        SignalEntity signal = Instancio.create(SignalEntity.class);
        signal.setId(null);
        signal.setAlgotradingId(algotradingId);
        signal.setCandleOpenTime(Instant.parse("2025-01-01T10:00:00Z"));
        signal.setSignalType(SignalType.BUY_OPEN);
        signal.setStatus(SignalStatus.DETECTED);
        signal.setTriggerPrice(new BigDecimal("100.25000000"));
        signal.setIndicatorSnapshot(Map.of("rsi", new BigDecimal("42.5"), "trend", "up"));
        SignalEntity persisted = signalRepository.saveAndFlush(signal);
        entityManager.clear();

        SignalEntity loaded = signalRepository.findByAlgotradingId(
                algotradingId,
                org.springframework.data.domain.PageRequest.of(0, 10)
        ).getContent().get(0);

        assertThat(loaded.getId()).isEqualTo(persisted.getId());
        assertThat(loaded.getIndicatorSnapshot()).containsEntry("trend", "up");
        assertThat(new BigDecimal(loaded.getIndicatorSnapshot().get("rsi").toString()))
                .isEqualByComparingTo("42.5");
    }

    @Test
    void incrementsOrderVersionWhenUpdated() {
        OrderEntity order = saveOrder();
        assertThat(order.getVersion()).isZero();

        order.setRejectReason("updated");
        orderRepository.saveAndFlush(order);

        assertThat(order.getVersion()).isEqualTo(1L);
    }

    @Test
    void incrementsPositionVersionWhenUpdated() {
        PositionEntity position = savePosition(PositionStatus.OPEN);
        assertThat(position.getVersion()).isZero();

        position.setCurrentStopLoss(new BigDecimal("95.00000000"));
        positionRepository.saveAndFlush(position);

        assertThat(position.getVersion()).isEqualTo(1L);
    }

    private OrderEntity saveOrder() {
        OrderEntity order = Instancio.create(OrderEntity.class);
        order.setId(null);
        order.setVersion(null);
        order.setBrokerClientId(brokerClientId);
        order.setInstrumentPairId(instrumentPairId);
        order.setPositionId(null);
        order.setAlgotradingId(null);
        order.setSignalId(null);
        order.setParentOrderId(null);
        order.setClientOrderId("test-order-" + UUID.randomUUID());
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setTimeInForce(TimeInForce.GTC);
        order.setQuantity(new BigDecimal("2.50000000"));
        order.setStatus(OrderStatus.OPEN);
        return orderRepository.saveAndFlush(order);
    }

    private PositionEntity savePosition(PositionStatus status) {
        PositionEntity position = Instancio.create(PositionEntity.class);
        position.setId(null);
        position.setVersion(null);
        position.setBrokerClientId(brokerClientId);
        position.setInstrumentPairId(instrumentPairId);
        position.setSide(PositionSide.LONG);
        position.setStatus(status);
        position.setQuantity(new BigDecimal("2.50000000"));
        position.setAvgEntryPrice(new BigDecimal("100.00000000"));
        position.setCurrency("USD");
        return positionRepository.saveAndFlush(position);
    }

    private void setOrderCreatedAt(Long orderId, Instant createdAt) {
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), orderId);
    }

    private void setPositionOpenedAt(Long positionId, Instant openedAt) {
        jdbcTemplate.update("UPDATE position SET opened_at = ? WHERE id = ?", Timestamp.from(openedAt), positionId);
    }

    private long insertAndReturnId(String sql, Object... args) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(sql, Long.class, args));
    }
}

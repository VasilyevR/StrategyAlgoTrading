package rv.trading.strategy.collector.feed.bybit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import rv.trading.strategy.collector.config.MarketCollectorProperties;
import rv.trading.strategy.collector.feed.ExchangeMarketDataFeed;
import rv.trading.strategy.collector.publisher.MarketEventPublisher;
import rv.trading.strategy.collector.service.CandleStorageService;
import rv.trading.strategy.contracts.dto.internal.MarketSubscription;
import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.enums.TimeframeDurations;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BybitMarketDataFeed implements ExchangeMarketDataFeed {

    private static final Logger LOGGER = LoggerFactory.getLogger(BybitMarketDataFeed.class);
    private static final int RECONNECT_BACKFILL_LIMIT = 5;

    private final BybitKlineRestClient klineRestClient;
    private final CandleStorageService candleStorageService;
    private final MarketEventPublisher marketEventPublisher;
    private final MarketCollectorProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, WebSocket> sockets;
    private final ConcurrentHashMap<String, MarketSubscription> tickerSubscriptions;
    private volatile boolean running;
    private volatile List<MarketSubscription> subscriptions;

    @Autowired
    public BybitMarketDataFeed(BybitKlineRestClient klineRestClient,
                               CandleStorageService candleStorageService,
                               MarketEventPublisher marketEventPublisher,
                               MarketCollectorProperties properties,
                               HttpClient httpClient,
                               ObjectMapper objectMapper) {
        this.klineRestClient = klineRestClient;
        this.candleStorageService = candleStorageService;
        this.marketEventPublisher = marketEventPublisher;
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.sockets = new ConcurrentHashMap<>();
        this.tickerSubscriptions = new ConcurrentHashMap<>();
    }

    @Override
    public BrokerCode brokerCode() {
        return BrokerCode.BYBIT;
    }

    @Override
    public void start(List<MarketSubscription> configuredSubscriptions) {
        if (configuredSubscriptions == null) {
            return;
        }
        subscriptions = List.copyOf(configuredSubscriptions);
        running = true;

        executor.execute(() -> backfill(subscriptions));

        for (String category : subscriptions.stream()
                .map(subscription -> categoryOrDefault(subscription.marketCategory()))
                .distinct()
                .toList()) {
            executor.execute(() -> connectWithReconnect(category));
        }
    }

    @Override
    public void stop() {
        running = false;
        for (WebSocket socket : sockets.values()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, BybitConstants.WEBSOCKET_SHUTDOWN_REASON);
        }
        sockets.clear();
        executor.close();
    }

    private void backfill(List<MarketSubscription> configuredSubscriptions) {
        int limit = properties.getBackfillLimit();
        for (MarketSubscription subscription : configuredSubscriptions) {
            if (subscription.timeframes() == null) {
                continue;
            }
            for (TimeframeCode timeframeCode : subscription.timeframes()) {
                try {
                    for (CandleEvent candle : klineRestClient.fetchHistoricalKlines(
                            subscription.instrumentPairId(),
                            subscription.symbol(),
                            subscription.marketCategory(),
                            timeframeCode,
                            limit)) {
                        candleStorageService.upsertCandle(candle);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Bybit historical kline backfill failed for {} {} {}",
                            subscription.symbol(), subscription.marketCategory(), timeframeCode,
                            exception);
                }
            }
        }
    }

    private void backfillCategoryOnReconnect(String category) {
        List<MarketSubscription> currentSubscriptions = subscriptions;
        if (currentSubscriptions == null) {
            return;
        }
        for (MarketSubscription subscription : currentSubscriptions) {
            if (!category.equals(categoryOrDefault(subscription.marketCategory()))
                    || subscription.timeframes() == null) {
                continue;
            }
            for (TimeframeCode timeframeCode : subscription.timeframes()) {
                try {
                    for (CandleEvent candle : klineRestClient.fetchHistoricalKlines(
                            subscription.instrumentPairId(),
                            subscription.symbol(),
                            subscription.marketCategory(),
                            timeframeCode,
                            RECONNECT_BACKFILL_LIMIT)) {
                        candleStorageService.upsertCandle(candle);
                        if (candle.closed()) {
                            marketEventPublisher.publish(candle);
                        }
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Bybit reconnect backfill failed for {} {} {}: {}",
                            subscription.symbol(), subscription.marketCategory(), timeframeCode,
                            exception.getMessage());
                }
            }
        }
    }

    private void connectWithReconnect(String category) {
        long initialDelayMillis = properties.getReconnectInitialDelayMillis();
        AtomicLong backoffMillis = new AtomicLong(initialDelayMillis);
        AtomicBoolean initialConnect = new AtomicBoolean(true);
        long maxBackoffMillis = properties.getReconnectMaxDelayMillis();

        while (running) {
            CountDownLatch disconnected = new CountDownLatch(1);
            AtomicBoolean connected = new AtomicBoolean(false);
            BybitWebSocketListener listener = new BybitWebSocketListener(
                    category, disconnected, connected, backoffMillis, initialConnect);
            String baseUrl = properties.getBybitWebSocketBaseUrl();
            String separator = baseUrl.endsWith("/") ? "" : "/";
            URI uri = URI.create(baseUrl + separator + category);
            try {
                WebSocket socket = httpClient.newWebSocketBuilder()
                        .buildAsync(uri, listener)
                        .join();
                sockets.put(category, socket);
                executor.execute(() -> heartbeat(socket, connected, category));
                disconnected.await();
                sockets.remove(category);
                connected.set(false);
                if (!running) {
                    return;
                }
                Thread.sleep(backoffMillis.get());
                backoffMillis.set(Math.min(backoffMillis.get() * 2, maxBackoffMillis));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                LOGGER.warn("Bybit public WebSocket connection failed for {}: {}",
                        category, exception.getMessage());
                if (!running) {
                    return;
                }
                try {
                    Thread.sleep(backoffMillis.get());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMillis.set(Math.min(backoffMillis.get() * 2, maxBackoffMillis));
            }
        }
    }

    private void heartbeat(WebSocket socket, AtomicBoolean connected, String category) {
        int intervalSeconds = properties.getHeartbeatIntervalSeconds();
        while (running && connected.get()) {
            try {
                Thread.sleep(Duration.ofSeconds(intervalSeconds).toMillis());
                if (running && connected.get()) {
                    ObjectNode ping = objectMapper.createObjectNode();
                    ping.put(BybitConstants.OPERATION_KEY, BybitConstants.OP_PING);
                    socket.sendText(ping.toString(), true);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                LOGGER.debug("Bybit heartbeat send failed for {}", category, exception);
                return;
            }
        }
    }

    private final class BybitWebSocketListener implements WebSocket.Listener {

        private final String category;
        private final CountDownLatch disconnected;
        private final AtomicBoolean connected;
        private final AtomicLong backoffMillis;
        private final AtomicBoolean initialConnect;
        private final StringBuilder frameBuffer = new StringBuilder();

        private BybitWebSocketListener(String category,
                                       CountDownLatch disconnected,
                                       AtomicBoolean connected,
                                       AtomicLong backoffMillis,
                                       AtomicBoolean initialConnect) {
            this.category = category;
            this.disconnected = disconnected;
            this.connected = connected;
            this.backoffMillis = backoffMillis;
            this.initialConnect = initialConnect;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            connected.set(true);
            backoffMillis.set(properties.getReconnectInitialDelayMillis());
            webSocket.request(Long.MAX_VALUE);
            subscribe(webSocket);
            if (!initialConnect.getAndSet(false)) {
                LOGGER.info("Bybit public WebSocket reconnected for {}, running gap recovery", category);
                executor.execute(() -> backfillCategoryOnReconnect(category));
            }
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(Long.MAX_VALUE);
            frameBuffer.append(data);
            if (last) {
                processMessage(frameBuffer.toString());
                frameBuffer.setLength(0);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.set(false);
            disconnected.countDown();
            if (!running) {
                return;
            }
            if (error instanceof IOException) {
                LOGGER.warn("Bybit public WebSocket disconnected for {} ({}), reconnecting...",
                        category, error.getMessage());
            } else {
                LOGGER.warn("Bybit public WebSocket error for {}", category, error);
            }
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected.set(false);
            disconnected.countDown();
            return CompletableFuture.completedFuture(null);
        }

        private void subscribe(WebSocket webSocket) {
            ArrayNode args = objectMapper.createArrayNode();
            for (MarketSubscription subscription : subscriptions) {
                if (!category.equals(categoryOrDefault(subscription.marketCategory()))) {
                    continue;
                }
                tickerSubscriptions.put(subscriptionKey(category, subscription.symbol()), subscription);
                args.add(BybitConstants.TICKER_TOPIC_PREFIX + subscription.symbol());
                if (subscription.timeframes() != null) {
                    for (TimeframeCode timeframeCode : subscription.timeframes()) {
                        args.add(BybitConstants.KLINE_TOPIC_PREFIX + BybitTimeframeMapper.map(timeframeCode)
                                + BybitConstants.TOPIC_DELIMITER + subscription.symbol());
                    }
                }
            }
            ObjectNode request = objectMapper.createObjectNode();
            request.put(BybitConstants.OPERATION_KEY, BybitConstants.OP_SUBSCRIBE);
            request.set(BybitConstants.ARGS_KEY, args);
            webSocket.sendText(request.toString(), true);
        }

        private void processMessage(String text) {
            try {
                JsonNode message = objectMapper.readTree(text);
                String topic = message.path(BybitConstants.TOPIC_KEY).asText();
                JsonNode topLevelTimestamp = message.path(BybitConstants.TIMESTAMP_KEY);
                if (topic.startsWith(BybitConstants.TICKER_TOPIC_PREFIX)) {
                    processTicker(topic, message.path(BybitConstants.DATA_KEY), topLevelTimestamp);
                } else if (topic.startsWith(BybitConstants.KLINE_TOPIC_PREFIX)) {
                    processKline(topic, message.path(BybitConstants.DATA_KEY));
                }
            } catch (RuntimeException exception) {
                LOGGER.debug("Ignoring malformed Bybit WebSocket message", exception);
            }
        }

        private void processTicker(String topic, JsonNode data, JsonNode topLevelTimestamp) {
            if (data.isArray()) {
                for (JsonNode item : data) {
                    processTickerItem(topic, item, topLevelTimestamp);
                }
            } else {
                processTickerItem(topic, data, topLevelTimestamp);
            }
        }

        private void processTickerItem(String topic, JsonNode data, JsonNode topLevelTimestamp) {
            if (data == null || data.isMissingNode()) {
                return;
            }
            String symbol = data.path(BybitConstants.SYMBOL_KEY).asText(symbolFromTopic(topic));
            MarketSubscription subscription = tickerSubscriptions.get(subscriptionKey(category, symbol));
            if (subscription == null) {
                return;
            }
            BigDecimal lastPrice = decimal(data, BybitConstants.LAST_PRICE_KEY);
            BigDecimal bidPrice = decimal(data, BybitConstants.BID_ONE_PRICE_KEY);
            BigDecimal askPrice = decimal(data, BybitConstants.ASK_ONE_PRICE_KEY);
            if (lastPrice == null || lastPrice.signum() <= 0) {
                return;
            }
            Instant timestamp = timestamp(topLevelTimestamp);
            marketEventPublisher.publish(new TickEvent(
                    subscription.instrumentPairId(),
                    symbol,
                    lastPrice,
                    bidPrice,
                    askPrice,
                    timestamp
            ));
        }

        private void processKline(String topic, JsonNode data) {
            String[] parts = topic.split(BybitConstants.TOPIC_DELIMITER_REGEX);
            if (parts.length < 3) {
                return;
            }
            String interval = parts[1];
            String symbol = parts[2];
            TimeframeCode timeframeCode = BybitTimeframeMapper.reverse(interval).orElse(null);
            if (timeframeCode == null) {
                return;
            }
            MarketSubscription subscription = tickerSubscriptions.get(subscriptionKey(category, symbol));
            if (subscription == null) {
                return;
            }
            if (data.isArray()) {
                for (JsonNode item : data) {
                    processKlineItem(subscription, timeframeCode, item);
                }
            } else {
                processKlineItem(subscription, timeframeCode, data);
            }
        }

        private void processKlineItem(MarketSubscription subscription,
                                      TimeframeCode timeframeCode,
                                      JsonNode data) {
            if (data == null || data.isMissingNode() || !data.path(BybitConstants.CONFIRM_KEY).asBoolean(false)) {
                return;
            }
            Instant openTime = epochFromMillis(data.path(BybitConstants.START_KEY).asText());
            if (openTime == null) {
                return;
            }
            Instant closeTime = openTime.plus(TimeframeDurations.duration(timeframeCode));
            BigDecimal open = decimal(data, BybitConstants.OPEN_KEY);
            BigDecimal high = decimal(data, BybitConstants.HIGH_KEY);
            BigDecimal low = decimal(data, BybitConstants.LOW_KEY);
            BigDecimal close = decimal(data, BybitConstants.CLOSE_KEY);
            BigDecimal volume = decimal(data, BybitConstants.VOLUME_KEY);
            if (open == null || high == null || low == null || close == null || volume == null) {
                return;
            }
            CandleEvent event = new CandleEvent(
                    subscription.instrumentPairId(),
                    subscription.symbol(),
                    timeframeCode,
                    openTime,
                    closeTime,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    true
            );
            candleStorageService.upsertCandle(event);
            marketEventPublisher.publish(event);
        }

        private Instant timestamp(JsonNode timestampNode) {
            String timestamp = timestampNode.asText();
            Instant result = epochFromMillis(timestamp);
            return result == null ? Instant.now() : result;
        }

        private String symbolFromTopic(String topic) {
            String[] parts = topic.split(BybitConstants.TOPIC_DELIMITER_REGEX);
            return parts.length > 1 ? parts[1] : "";
        }
    }

    private static String subscriptionKey(String category, String symbol) {
        return category + BybitConstants.TOPIC_DELIMITER + symbol;
    }

    private static String categoryOrDefault(String marketCategory) {
        return marketCategory == null || marketCategory.isBlank()
                ? BybitConstants.LINEAR_CATEGORY
                : marketCategory;
    }

    private static BigDecimal decimal(JsonNode data, String field) {
        if (data == null || data.isMissingNode() || !data.has(field)) {
            return null;
        }
        String value = data.path(field).asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Instant epochFromMillis(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

}

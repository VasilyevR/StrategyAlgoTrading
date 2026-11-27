package rv.trading.strategy.collector.feed.bybit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.instancio.Instancio;
import org.instancio.Select;
import rv.trading.strategy.collector.config.MarketCollectorProperties;
import rv.trading.strategy.collector.publisher.MarketEventPublisher;
import rv.trading.strategy.collector.service.CandleStorageService;
import rv.trading.strategy.contracts.dto.internal.MarketSubscription;
import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BybitMarketDataFeedTest {

    private static final long INSTRUMENT_PAIR_ID = 101L;
    private static final String SYMBOL = "BTCUSDT";
    private static final long MESSAGE_TIMESTAMP_MILLIS = 1_764_000_000_000L;

    private final BybitKlineRestClient klineRestClient = mock(BybitKlineRestClient.class);
    private final CandleStorageService candleStorageService = mock(CandleStorageService.class);
    private final MarketEventPublisher marketEventPublisher = mock(MarketEventPublisher.class);
    private final MarketCollectorProperties properties = new MarketCollectorProperties();
    private final HttpClient httpClient = mock(HttpClient.class);
    private final WebSocket.Builder webSocketBuilder = mock(WebSocket.Builder.class);
    private final WebSocket webSocket = mock(WebSocket.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AtomicReference<WebSocket.Listener> listenerReference = new AtomicReference<>();
    private final CountDownLatch listenerCaptured = new CountDownLatch(1);
    private BybitMarketDataFeed feed;

    @AfterEach
    void stopFeed() {
        if (feed != null) {
            WebSocket.Listener listener = listenerReference.get();
            if (listener != null) {
                listener.onClose(webSocket, WebSocket.NORMAL_CLOSURE, "test-stop");
            }
            feed.stop();
        }
    }

    @Test
    void backfillsSubscribesReassemblesFramesAndProcessesTickerAndKlineMessages() throws Exception {
        CandleEvent backfillCandle = candle(Instant.parse("2025-01-01T10:00:00Z"));
        when(klineRestClient.fetchHistoricalKlines(
                INSTRUMENT_PAIR_ID, SYMBOL, BybitConstants.LINEAR_CATEGORY, TimeframeCode.M1, 5))
                .thenReturn(List.of(backfillCandle));
        configureWebSocket();
        feed = createFeed();

        feed.start(List.of(subscription()));

        assertThat(listenerCaptured.await(2, TimeUnit.SECONDS)).isTrue();
        verify(candleStorageService, timeout(TimeUnit.SECONDS.toMillis(2))).upsertCandle(backfillCandle);
        WebSocket.Listener listener = listenerReference.get();
        listener.onOpen(webSocket);

        org.mockito.ArgumentCaptor<CharSequence> subscriptionRequest =
                org.mockito.ArgumentCaptor.forClass(CharSequence.class);
        verify(webSocket).sendText(subscriptionRequest.capture(), anyBoolean());
        assertThat(subscriptionRequest.getValue().toString())
                .contains(BybitConstants.TICKER_TOPIC_PREFIX + SYMBOL)
                .contains(BybitConstants.KLINE_TOPIC_PREFIX + BybitConstants.INTERVAL_1_MINUTE
                        + BybitConstants.TOPIC_DELIMITER + SYMBOL);

        String tickerSnapshot = tickerSnapshot();
        int splitAt = tickerSnapshot.length() / 2;
        listener.onText(webSocket, tickerSnapshot.substring(0, splitAt), false);
        verifyNoInteractions(marketEventPublisher);
        listener.onText(webSocket, tickerSnapshot.substring(splitAt), true);

        listener.onText(webSocket, tickerDelta("101.0"), true);
        listener.onText(webSocket, tickerDelta(null), true);
        listener.onText(webSocket, tickerDelta("0"), true);
        listener.onText(webSocket, klineMessage(false, true), true);
        listener.onText(webSocket, klineMessage(true, false), true);
        listener.onText(webSocket, klineMessage(true, true), true);

        org.mockito.ArgumentCaptor<TickEvent> ticks = org.mockito.ArgumentCaptor.forClass(TickEvent.class);
        verify(marketEventPublisher, times(2)).publish(ticks.capture());
        TickEvent tick = ticks.getAllValues().getFirst();
        assertThat(tick.instrumentPairId()).isEqualTo(INSTRUMENT_PAIR_ID);
        assertThat(tick.symbol()).isEqualTo(SYMBOL);
        assertThat(tick.lastPrice()).isEqualTo(new BigDecimal("100.25"));
        assertThat(tick.bidPrice()).isEqualTo(new BigDecimal("100.20"));
        assertThat(tick.askPrice()).isEqualTo(new BigDecimal("100.30"));
        assertThat(tick.timestamp()).isEqualTo(Instant.ofEpochMilli(MESSAGE_TIMESTAMP_MILLIS));
        TickEvent deltaTick = ticks.getAllValues().getLast();
        assertThat(deltaTick.lastPrice()).isEqualTo(new BigDecimal("101.0"));
        assertThat(deltaTick.bidPrice()).isNull();
        assertThat(deltaTick.askPrice()).isNull();

        org.mockito.ArgumentCaptor<CandleEvent> candles =
                org.mockito.ArgumentCaptor.forClass(CandleEvent.class);
        verify(marketEventPublisher, times(1)).publish(candles.capture());
        CandleEvent publishedCandle = candles.getValue();
        assertThat(publishedCandle.open()).isEqualTo(new BigDecimal("10.5"));
        assertThat(publishedCandle.high()).isEqualTo(new BigDecimal("12.5"));
        assertThat(publishedCandle.low()).isEqualTo(new BigDecimal("9.5"));
        assertThat(publishedCandle.close()).isEqualTo(new BigDecimal("11.5"));
        assertThat(publishedCandle.volume()).isEqualTo(new BigDecimal("20.5"));
        assertThat(publishedCandle.closeTime())
                .isEqualTo(Instant.ofEpochMilli(MESSAGE_TIMESTAMP_MILLIS).plusSeconds(60));
        assertThat(publishedCandle.closed()).isTrue();
        verify(candleStorageService, times(2)).upsertCandle(any(CandleEvent.class));
    }

    private BybitMarketDataFeed createFeed() {
        properties.setBybitWebSocketBaseUrl("wss://example.test/v5/public/");
        properties.setBackfillLimit(5);
        properties.setHeartbeatIntervalSeconds(1);
        properties.setReconnectInitialDelayMillis(1);
        properties.setReconnectMaxDelayMillis(2);
        return new BybitMarketDataFeed(
                klineRestClient, candleStorageService, marketEventPublisher, properties,
                httpClient, objectMapper);
    }

    private void configureWebSocket() {
        when(httpClient.newWebSocketBuilder()).thenReturn(webSocketBuilder);
        when(webSocket.sendText(any(CharSequence.class), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        doAnswer(invocation -> {
            WebSocket.Listener listener = listenerReference.get();
            if (listener != null) {
                listener.onClose(webSocket, invocation.getArgument(0), invocation.getArgument(1));
            }
            return CompletableFuture.completedFuture(webSocket);
        }).when(webSocket).sendClose(anyInt(), anyString());
        doAnswer(invocation -> {
            listenerReference.set(invocation.getArgument(1));
            listenerCaptured.countDown();
            return CompletableFuture.completedFuture(webSocket);
        }).when(webSocketBuilder).buildAsync(any(URI.class), any(WebSocket.Listener.class));
    }

    private static MarketSubscription subscription() {
        return Instancio.of(MarketSubscription.class)
                .set(Select.field(MarketSubscription.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(MarketSubscription.class, "brokerCode"), BrokerCode.BYBIT)
                .set(Select.field(MarketSubscription.class, "symbol"), SYMBOL)
                .set(Select.field(MarketSubscription.class, "marketCategory"), BybitConstants.LINEAR_CATEGORY)
                .set(Select.field(MarketSubscription.class, "timeframes"), List.of(TimeframeCode.M1))
                .create();
    }

    private String tickerSnapshot() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(BybitConstants.SYMBOL_KEY, SYMBOL);
        data.put(BybitConstants.LAST_PRICE_KEY, "100.25");
        data.put(BybitConstants.BID_ONE_PRICE_KEY, "100.20");
        data.put(BybitConstants.ASK_ONE_PRICE_KEY, "100.30");
        ObjectNode message = objectMapper.createObjectNode();
        message.put(BybitConstants.TOPIC_KEY, BybitConstants.TICKER_TOPIC_PREFIX + SYMBOL);
        message.put(BybitConstants.TIMESTAMP_KEY, MESSAGE_TIMESTAMP_MILLIS);
        message.set(BybitConstants.DATA_KEY, data);
        return objectMapper.writeValueAsString(message);
    }

    private String tickerDelta(String lastPrice) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(BybitConstants.SYMBOL_KEY, SYMBOL);
        if (lastPrice != null) {
            data.put(BybitConstants.LAST_PRICE_KEY, lastPrice);
        }
        ObjectNode message = objectMapper.createObjectNode();
        message.put(BybitConstants.TOPIC_KEY, BybitConstants.TICKER_TOPIC_PREFIX + SYMBOL);
        message.put(BybitConstants.TIMESTAMP_KEY, MESSAGE_TIMESTAMP_MILLIS);
        message.set(BybitConstants.DATA_KEY, data);
        return objectMapper.writeValueAsString(message);
    }

    private String klineMessage(boolean confirmed, boolean complete) {
        ObjectNode candle = objectMapper.createObjectNode();
        candle.put(BybitConstants.CONFIRM_KEY, confirmed);
        candle.put(BybitConstants.START_KEY, MESSAGE_TIMESTAMP_MILLIS);
        candle.put(BybitConstants.END_KEY, MESSAGE_TIMESTAMP_MILLIS + 59_999);
        candle.put(BybitConstants.OPEN_KEY, "10.5");
        if (complete) {
            candle.put(BybitConstants.HIGH_KEY, "12.5");
            candle.put(BybitConstants.LOW_KEY, "9.5");
        } else {
            candle.putNull(BybitConstants.HIGH_KEY);
            candle.putNull(BybitConstants.LOW_KEY);
        }
        candle.put(BybitConstants.CLOSE_KEY, "11.5");
        candle.put(BybitConstants.VOLUME_KEY, "20.5");
        ObjectNode message = objectMapper.createObjectNode();
        message.put(BybitConstants.TOPIC_KEY, BybitConstants.KLINE_TOPIC_PREFIX
                + BybitConstants.INTERVAL_1_MINUTE + BybitConstants.TOPIC_DELIMITER + SYMBOL);
        message.set(BybitConstants.DATA_KEY, objectMapper.createArrayNode().add(candle));
        return objectMapper.writeValueAsString(message);
    }

    private static CandleEvent candle(Instant openTime) {
        return Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(CandleEvent.class, "symbol"), SYMBOL)
                .set(Select.field(CandleEvent.class, "timeframeCode"), TimeframeCode.M1)
                .set(Select.field(CandleEvent.class, "openTime"), openTime)
                .set(Select.field(CandleEvent.class, "closeTime"), openTime.plusSeconds(60))
                .set(Select.field(CandleEvent.class, "open"), new BigDecimal("1.0"))
                .set(Select.field(CandleEvent.class, "high"), new BigDecimal("2.0"))
                .set(Select.field(CandleEvent.class, "low"), new BigDecimal("0.5"))
                .set(Select.field(CandleEvent.class, "close"), new BigDecimal("1.5"))
                .set(Select.field(CandleEvent.class, "volume"), new BigDecimal("4.0"))
                .set(Select.field(CandleEvent.class, "closed"), true)
                .create();
    }
}

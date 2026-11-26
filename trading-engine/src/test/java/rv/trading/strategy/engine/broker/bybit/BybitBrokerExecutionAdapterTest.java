package rv.trading.strategy.engine.broker.bybit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.TimeInForce;
import rv.trading.strategy.engine.broker.DecryptedBrokerCredentials;
import rv.trading.strategy.engine.config.TradingEngineProperties;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BybitBrokerExecutionAdapterTest {
    private static final String API_KEY = "wiremock-api-key";
    private static final String API_SECRET = "wiremock-api-secret";
    private static final String SYMBOL = "BTCUSDT";
    private static final String CATEGORY = BybitBrokerExecutionAdapter.LINEAR;
    private static final String RET_CODE_KEY = BybitBrokerExecutionAdapter.RET_CODE_KEY;
    private static final String RET_MSG_KEY = BybitBrokerExecutionAdapter.RET_MSG_KEY;
    private static final String RESULT_KEY = BybitBrokerExecutionAdapter.RESULT_KEY;
    private static final String ORDER_ID_KEY = BybitBrokerExecutionAdapter.ORDER_ID_KEY;
    private static final String ORDER_STATUS_KEY = BybitBrokerExecutionAdapter.ORDER_STATUS_KEY;
    private static final String FILLED_STATUS = BybitBrokerExecutionAdapter.FILLED_STATUS;
    private static final String CUM_EXEC_QTY_KEY = BybitBrokerExecutionAdapter.CUM_EXEC_QTY_KEY;
    private static final String AVG_PRICE_KEY = BybitBrokerExecutionAdapter.AVG_PRICE_KEY;
    private static final String CUM_EXEC_FEE_KEY = BybitBrokerExecutionAdapter.CUM_EXEC_FEE_KEY;
    private static final String ORDER_TYPE_KEY = BybitBrokerExecutionAdapter.ORDER_TYPE_KEY;
    private static final String SIDE_KEY = BybitBrokerExecutionAdapter.SIDE_KEY;
    private static final String QUANTITY_KEY = BybitBrokerExecutionAdapter.QUANTITY_KEY;
    private static final String REDUCE_ONLY_KEY = BybitBrokerExecutionAdapter.REDUCE_ONLY_KEY;
    private static final String TRIGGER_PRICE_KEY = BybitBrokerExecutionAdapter.TRIGGER_PRICE_KEY;
    private static final String TRIGGER_DIRECTION_KEY = BybitBrokerExecutionAdapter.TRIGGER_DIRECTION_KEY;
    private static final String SYMBOL_KEY = BybitBrokerExecutionAdapter.SYMBOL_PARAM;
    private static final String ACCOUNT_TYPE_KEY = BybitBrokerExecutionAdapter.ACCOUNT_TYPE_PARAM;
    private static final String UNIFIED = BybitBrokerExecutionAdapter.UNIFIED;
    private static final long RECV_WINDOW_MILLIS = 5_000L;
    private static final String QUERY_EQUALS = BybitBrokerExecutionAdapter.QUERY_EQUALS;
    private static final String QUERY_SEPARATOR = BybitBrokerExecutionAdapter.QUERY_SEPARATOR;
    private static final String CLIENT_MARKET_ORDER_ID = "client-market-order";
    private static final String CLIENT_STOP_ORDER_ID = "client-stop-order";
    private static final String INVALID_ORDER_MESSAGE = "invalid order";
    private static final String MISSING_ORDER_MESSAGE = "order not exists";
    private static final String SUCCESS_MESSAGE = "OK";
    private static final String BALANCE_RESPONSE_VALUE = "125.50";
    private static final String PAPER_BALANCE_RESPONSE_VALUE = "42.25";
    private static final String COIN_BALANCE_RESPONSE_VALUE = "16.75";
    private static final String FILLED_QUANTITY = "0.75";
    private static final String FILLED_AVERAGE_PRICE = "64000.5";
    private static final String FILLED_COMMISSION = "0.12";
    private static final String STOP_PRICE = "61234.50";
    private static final String AMENDED_STOP_PRICE = "61000.25";
    private static final int SUCCESS_CODE = 0;
    private static final int ORDER_REJECTED_CODE = 10001;
    private static final int EXCHANGE_ERROR_CODE = 110001;
    private static final long INSTRUMENT_PAIR_ID = 83L;
    private static final String EXCHANGE_ORDER_ID = "exchange-order-91";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private WireMockServer liveServer;
    private WireMockServer demoServer;
    private BybitBrokerExecutionAdapter adapter;

    @BeforeEach
    void startServersAndAdapter() {
        liveServer = new WireMockServer(options().dynamicPort());
        demoServer = new WireMockServer(options().dynamicPort());
        liveServer.start();
        demoServer.start();
        TradingEngineProperties properties = new TradingEngineProperties();
        properties.setBybitLiveBaseUrl(liveServer.baseUrl());
        properties.setBybitDemoBaseUrl(demoServer.baseUrl());
        properties.setBybitRecvWindowMillis(RECV_WINDOW_MILLIS);
        adapter = new BybitBrokerExecutionAdapter(properties, JSON_MAPPER, runtimeConfigPort());
    }

    @AfterEach
    void stopServers() {
        liveServer.stop();
        demoServer.stop();
    }

    @Test
    void routesPaperAndLiveRequestsToTheirConfiguredHostsWithSignedHeaders() {
        liveServer.stubFor(get(urlPathEqualTo(BybitV5Endpoint.GET_WALLET.path()))
                .willReturn(jsonResponse(walletBalanceResponse(BALANCE_RESPONSE_VALUE))));
        demoServer.stubFor(get(urlPathEqualTo(BybitV5Endpoint.GET_WALLET.path()))
                .willReturn(jsonResponse(walletBalanceResponse(PAPER_BALANCE_RESPONSE_VALUE))));

        BigDecimal demoBalance = adapter.getAvailableBalance(credentials(true), INSTRUMENT_PAIR_ID);
        BigDecimal liveBalance = adapter.getAvailableBalance(credentials(false), INSTRUMENT_PAIR_ID);

        assertThat(demoBalance).isEqualByComparingTo(PAPER_BALANCE_RESPONSE_VALUE);
        assertThat(liveBalance).isEqualByComparingTo(BALANCE_RESPONSE_VALUE);
        demoServer.verify(getRequestedFor(urlPathEqualTo(BybitV5Endpoint.GET_WALLET.path()))
                .withQueryParam(ACCOUNT_TYPE_KEY, equalTo(UNIFIED)));
        liveServer.verify(getRequestedFor(urlPathEqualTo(BybitV5Endpoint.GET_WALLET.path()))
                .withQueryParam(ACCOUNT_TYPE_KEY, equalTo(UNIFIED)));
        assertSignature(demoServer.getAllServeEvents().getFirst().getRequest(),
                ACCOUNT_TYPE_KEY + "=" + UNIFIED);
        assertSignature(liveServer.getAllServeEvents().getFirst().getRequest(),
                ACCOUNT_TYPE_KEY + "=" + UNIFIED);
    }

    @Test
    void getsAvailableBalanceFromUsdtCoinWhenTotalIsUnavailable() {
        demoServer.stubFor(get(urlPathEqualTo(BybitV5Endpoint.GET_WALLET.path()))
                .willReturn(jsonResponse(walletCoinBalanceResponse())));

        BigDecimal balance = adapter.getAvailableBalance(credentials(true), INSTRUMENT_PAIR_ID);

        assertThat(balance).isEqualByComparingTo(COIN_BALANCE_RESPONSE_VALUE);
    }

    @Test
    void placesMarketOrderAndReturnsExchangeFillDetails() {
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.CREATE_ORDER.path()))
                .willReturn(jsonResponse(orderCreatedResponse())));
        demoServer.stubFor(get(urlPathEqualTo(BybitV5Endpoint.REALTIME_ORDER.path()))
                .willReturn(jsonResponse(filledOrderResponse())));

        OrderExecutionReceipt receipt = adapter.placeOrder(credentials(true), marketOrder());

        assertThat(receipt.exchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
        assertThat(receipt.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(receipt.filledQuantity()).isEqualByComparingTo(FILLED_QUANTITY);
        assertThat(receipt.avgFillPrice()).isEqualByComparingTo(FILLED_AVERAGE_PRICE);
        assertThat(receipt.commission()).isEqualByComparingTo(FILLED_COMMISSION);
        demoServer.verify(postRequestedFor(urlPathEqualTo(BybitV5Endpoint.CREATE_ORDER.path())));
        LoggedRequest createRequest = requestFor(demoServer, BybitV5Endpoint.CREATE_ORDER);
        JsonNode requestBody = JSON_MAPPER.readTree(createRequest.getBodyAsString());
        assertThat(requestBody.path(ORDER_TYPE_KEY).asString()).isEqualTo(BybitBrokerExecutionAdapter.MARKET_ORDER_TYPE);
        assertThat(requestBody.path(SIDE_KEY).asString()).isEqualTo(BybitBrokerExecutionAdapter.BUY_SIDE);
        assertThat(requestBody.path(QUANTITY_KEY).asString()).isEqualTo(FILLED_QUANTITY);
        assertSignature(createRequest, createRequest.getBodyAsString());
        LoggedRequest realtimeRequest = requestFor(demoServer, BybitV5Endpoint.REALTIME_ORDER);
        assertSignature(realtimeRequest, BybitBrokerExecutionAdapter.CATEGORY_PARAM + QUERY_EQUALS + CATEGORY
                + QUERY_SEPARATOR + SYMBOL_KEY + QUERY_EQUALS + SYMBOL + QUERY_SEPARATOR
                + BybitBrokerExecutionAdapter.ORDER_ID_PARAM + QUERY_EQUALS + EXCHANGE_ORDER_ID);
    }

    @Test
    void returnsRejectedReceiptWhenMarketOrderIsRejected() {
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.CREATE_ORDER.path()))
                .willReturn(jsonResponse(exchangeResponse(ORDER_REJECTED_CODE, INVALID_ORDER_MESSAGE))));

        OrderExecutionReceipt receipt = adapter.placeOrder(credentials(true), marketOrder());

        assertThat(receipt.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(receipt.rejectReason()).isEqualTo(INVALID_ORDER_MESSAGE);
        assertThat(demoServer.getAllServeEvents()).hasSize(1);
    }

    @Test
    void placesReduceOnlyStopMarketOrderWithTriggerDirection() {
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.CREATE_ORDER.path()))
                .willReturn(jsonResponse(orderCreatedResponse())));
        PlaceOrderCommand command = Instancio.of(PlaceOrderCommand.class)
                .set(Select.field(PlaceOrderCommand.class, "brokerClientId"), 17L)
                .set(Select.field(PlaceOrderCommand.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(PlaceOrderCommand.class, "symbol"), SYMBOL)
                .set(Select.field(PlaceOrderCommand.class, "clientOrderId"), CLIENT_STOP_ORDER_ID)
                .set(Select.field(PlaceOrderCommand.class, "side"), OrderSide.SELL)
                .set(Select.field(PlaceOrderCommand.class, "orderType"), OrderType.STOP)
                .set(Select.field(PlaceOrderCommand.class, "timeInForce"), TimeInForce.GTC)
                .set(Select.field(PlaceOrderCommand.class, "quantity"), new BigDecimal(FILLED_QUANTITY))
                .set(Select.field(PlaceOrderCommand.class, "price"), null)
                .set(Select.field(PlaceOrderCommand.class, "stopPrice"), new BigDecimal(STOP_PRICE))
                .set(Select.field(PlaceOrderCommand.class, "reduceOnly"), true)
                .create();

        OrderExecutionReceipt receipt = adapter.placeOrder(credentials(true), command);

        assertThat(receipt.status()).isEqualTo(OrderStatus.OPEN);
        assertThat(receipt.exchangeOrderId()).isEqualTo(EXCHANGE_ORDER_ID);
        JsonNode body = JSON_MAPPER.readTree(requestFor(demoServer, BybitV5Endpoint.CREATE_ORDER).getBodyAsString());
        assertThat(body.path(ORDER_TYPE_KEY).asString()).isEqualTo(BybitBrokerExecutionAdapter.MARKET_ORDER_TYPE);
        assertThat(body.path(TRIGGER_PRICE_KEY).asString()).isEqualTo(STOP_PRICE);
        assertThat(body.path(TRIGGER_DIRECTION_KEY).asInt()).isEqualTo(2);
        assertThat(body.path(REDUCE_ONLY_KEY).asBoolean()).isTrue();
    }

    @Test
    void amendsStopLossAndFailsLoudlyOnExchangeError() {
        AmendStopLossCommand command = amendCommand();
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.AMEND_ORDER.path()))
                .willReturn(jsonResponse(exchangeResponse(SUCCESS_CODE, SUCCESS_MESSAGE))));

        adapter.amendStopLoss(credentials(true), command);

        JsonNode successBody = JSON_MAPPER.readTree(requestFor(demoServer, BybitV5Endpoint.AMEND_ORDER).getBodyAsString());
        assertThat(successBody.path(BybitBrokerExecutionAdapter.ORDER_ID_PARAM).asString()).isEqualTo(EXCHANGE_ORDER_ID);
        assertThat(successBody.path(TRIGGER_PRICE_KEY).asString()).isEqualTo(AMENDED_STOP_PRICE);
        demoServer.resetAll();
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.AMEND_ORDER.path()))
                .willReturn(jsonResponse(exchangeResponse(EXCHANGE_ERROR_CODE, MISSING_ORDER_MESSAGE))));

        assertThatThrownBy(() -> adapter.amendStopLoss(credentials(true), command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MISSING_ORDER_MESSAGE);
    }

    @Test
    void cancelsOrderIncludingResolvedSymbolAndFailsLoudlyOnExchangeError() {
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.CANCEL_ORDER.path()))
                .willReturn(jsonResponse(exchangeResponse(SUCCESS_CODE, SUCCESS_MESSAGE))));

        adapter.cancelOrder(credentials(true), INSTRUMENT_PAIR_ID, EXCHANGE_ORDER_ID);

        JsonNode successBody = JSON_MAPPER.readTree(requestFor(demoServer, BybitV5Endpoint.CANCEL_ORDER).getBodyAsString());
        assertThat(successBody.path(SYMBOL_KEY).asString()).isEqualTo(SYMBOL);
        assertThat(successBody.path(ORDER_ID_KEY).asString()).isEqualTo(EXCHANGE_ORDER_ID);
        demoServer.resetAll();
        demoServer.stubFor(post(urlPathEqualTo(BybitV5Endpoint.CANCEL_ORDER.path()))
                .willReturn(jsonResponse(exchangeResponse(EXCHANGE_ERROR_CODE, MISSING_ORDER_MESSAGE))));

        assertThatThrownBy(() -> adapter.cancelOrder(credentials(true), INSTRUMENT_PAIR_ID, EXCHANGE_ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MISSING_ORDER_MESSAGE);
    }

    @Test
    void failsToCancelWhenNoRuntimeSymbolIsAvailable() {
        TradingEngineProperties properties = new TradingEngineProperties();
        properties.setBybitLiveBaseUrl(liveServer.baseUrl());
        properties.setBybitDemoBaseUrl(demoServer.baseUrl());
        properties.setBybitRecvWindowMillis(RECV_WINDOW_MILLIS);
        BybitBrokerExecutionAdapter noRuntimeConfigAdapter = new BybitBrokerExecutionAdapter(
                properties, JSON_MAPPER, emptyRuntimeConfigPort());

        assertThatThrownBy(() -> noRuntimeConfigAdapter.cancelOrder(credentials(true), INSTRUMENT_PAIR_ID,
                EXCHANGE_ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Long.toString(INSTRUMENT_PAIR_ID));
    }

    private static void assertSignature(LoggedRequest request, String payload) {
        String timestamp = request.getHeader(BybitBrokerExecutionAdapter.X_BAPI_TIMESTAMP);
        assertThat(request.getHeader(BybitBrokerExecutionAdapter.X_BAPI_API_KEY)).isEqualTo(API_KEY);
        assertThat(request.getHeader(BybitBrokerExecutionAdapter.X_BAPI_RECV_WINDOW))
                .isEqualTo(Long.toString(RECV_WINDOW_MILLIS));
        assertThat(request.getHeader(BybitBrokerExecutionAdapter.X_BAPI_SIGN))
                .isEqualTo(BybitV5Signer.sign(API_KEY, API_SECRET, Long.parseLong(timestamp),
                        RECV_WINDOW_MILLIS, payload));
    }

    private static LoggedRequest requestFor(WireMockServer server, BybitV5Endpoint endpoint) {
        return server.getAllServeEvents().stream()
                .map(com.github.tomakehurst.wiremock.stubbing.ServeEvent::getRequest)
                .filter(request -> request.getUrl().contains(endpoint.path()))
                .findFirst()
                .orElseThrow();
    }

    private static ResponseDefinitionBuilder jsonResponse(JsonNode body) {
        try {
            return aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .withBody(JSON_MAPPER.writeValueAsString(body));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JsonNode walletBalanceResponse(String balance) {
        ObjectNode response = responseRoot(SUCCESS_CODE);
        response.putObject(RESULT_KEY).put(BybitBrokerExecutionAdapter.TOTAL_AVAILABLE_BALANCE_KEY, balance);
        return response;
    }

    private static JsonNode walletCoinBalanceResponse() {
        ObjectNode response = responseRoot(SUCCESS_CODE);
        ObjectNode wallet = response.putObject(RESULT_KEY).putArray(BybitBrokerExecutionAdapter.LIST_KEY)
                .addObject();
        wallet.putArray(BybitBrokerExecutionAdapter.COIN_KEY).addObject()
                .put(BybitBrokerExecutionAdapter.COIN_KEY, BybitBrokerExecutionAdapter.USDT)
                .put(BybitBrokerExecutionAdapter.AVAILABLE_TO_WITHDRAW_KEY, COIN_BALANCE_RESPONSE_VALUE);
        return response;
    }

    private static JsonNode orderCreatedResponse() {
        ObjectNode response = responseRoot(SUCCESS_CODE);
        response.putObject(RESULT_KEY).put(ORDER_ID_KEY, EXCHANGE_ORDER_ID);
        return response;
    }

    private static JsonNode filledOrderResponse() {
        ObjectNode order = JSON_MAPPER.createObjectNode();
        order.put(ORDER_STATUS_KEY, FILLED_STATUS);
        order.put(CUM_EXEC_QTY_KEY, FILLED_QUANTITY);
        order.put(AVG_PRICE_KEY, FILLED_AVERAGE_PRICE);
        order.put(CUM_EXEC_FEE_KEY, FILLED_COMMISSION);
        ObjectNode response = responseRoot(SUCCESS_CODE);
        response.putObject(RESULT_KEY).putArray(BybitBrokerExecutionAdapter.LIST_KEY).add(order);
        return response;
    }

    private static JsonNode exchangeResponse(int retCode, String retMsg) {
        ObjectNode response = responseRoot(retCode);
        response.put(RET_MSG_KEY, retMsg);
        return response;
    }

    private static ObjectNode responseRoot(int retCode) {
        ObjectNode response = JSON_MAPPER.createObjectNode();
        response.put(RET_CODE_KEY, retCode);
        return response;
    }

    private static DecryptedBrokerCredentials credentials(boolean paper) {
        return Instancio.of(DecryptedBrokerCredentials.class)
                .set(Select.field(DecryptedBrokerCredentials.class, "brokerClientId"), 17L)
                .set(Select.field(DecryptedBrokerCredentials.class, "brokerCode"), BrokerCode.BYBIT)
                .set(Select.field(DecryptedBrokerCredentials.class, "apiKey"), API_KEY)
                .set(Select.field(DecryptedBrokerCredentials.class, "apiSecret"), API_SECRET)
                .set(Select.field(DecryptedBrokerCredentials.class, "paper"), paper)
                .set(Select.field(DecryptedBrokerCredentials.class, "marketCategory"), CATEGORY)
                .create();
    }

    private static PlaceOrderCommand marketOrder() {
        return Instancio.of(PlaceOrderCommand.class)
                .set(Select.field(PlaceOrderCommand.class, "brokerClientId"), 17L)
                .set(Select.field(PlaceOrderCommand.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(PlaceOrderCommand.class, "symbol"), SYMBOL)
                .set(Select.field(PlaceOrderCommand.class, "clientOrderId"), CLIENT_MARKET_ORDER_ID)
                .set(Select.field(PlaceOrderCommand.class, "side"), OrderSide.BUY)
                .set(Select.field(PlaceOrderCommand.class, "orderType"), OrderType.MARKET)
                .set(Select.field(PlaceOrderCommand.class, "timeInForce"), TimeInForce.GTC)
                .set(Select.field(PlaceOrderCommand.class, "quantity"), new BigDecimal(FILLED_QUANTITY))
                .set(Select.field(PlaceOrderCommand.class, "price"), null)
                .set(Select.field(PlaceOrderCommand.class, "stopPrice"), null)
                .set(Select.field(PlaceOrderCommand.class, "reduceOnly"), false)
                .create();
    }

    private static AmendStopLossCommand amendCommand() {
        return Instancio.of(AmendStopLossCommand.class)
                .set(Select.field(AmendStopLossCommand.class, "brokerClientId"), 17L)
                .set(Select.field(AmendStopLossCommand.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(AmendStopLossCommand.class, "symbol"), SYMBOL)
                .set(Select.field(AmendStopLossCommand.class, "exchangeOrderId"), EXCHANGE_ORDER_ID)
                .set(Select.field(AmendStopLossCommand.class, "newStopPrice"), new BigDecimal(AMENDED_STOP_PRICE))
                .create();
    }

    private static RuntimeConfigPort runtimeConfigPort() {
        AlgotradingRuntimeConfig config = Instancio.of(AlgotradingRuntimeConfig.class)
                .set(Select.field(AlgotradingRuntimeConfig.class, "algotradingId"), 91L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "brokerClientId"), 17L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(AlgotradingRuntimeConfig.class, "symbol"), SYMBOL)
                .create();
        return runtimeConfigPort(List.of(config));
    }

    private static RuntimeConfigPort emptyRuntimeConfigPort() {
        return runtimeConfigPort(List.of());
    }

    private static RuntimeConfigPort runtimeConfigPort(List<AlgotradingRuntimeConfig> configs) {
        return new RuntimeConfigPort() {
            @Override
            public List<AlgotradingRuntimeConfig> loadActiveConfigs() {
                return configs;
            }

            @Override
            public Optional<AlgotradingRuntimeConfig> loadConfig(long algotradingId) {
                return configs.stream().filter(config -> config.algotradingId() == algotradingId).findFirst();
            }

            @Override
            public void reportWorkerStatus(long algotradingId,
                                           rv.trading.strategy.contracts.enums.AlgotradingStatus status,
                                           String errorMessage) {
            }
        };
    }

}

package rv.trading.strategy.engine.broker.bybit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.engine.broker.BrokerTradingAdapter;
import rv.trading.strategy.engine.broker.DecryptedBrokerCredentials;
import rv.trading.strategy.engine.config.TradingEngineProperties;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class BybitBrokerExecutionAdapter implements BrokerTradingAdapter {
    public static final String X_BAPI_API_KEY = "X-BAPI-API-KEY";
    public static final String X_BAPI_TIMESTAMP = "X-BAPI-TIMESTAMP";
    public static final String X_BAPI_RECV_WINDOW = "X-BAPI-RECV-WINDOW";
    public static final String X_BAPI_SIGN = "X-BAPI-SIGN";
    public static final String CATEGORY_PARAM = "category";
    public static final String SYMBOL_PARAM = "symbol";
    public static final String ORDER_ID_PARAM = "orderId";
    public static final String ACCOUNT_TYPE_PARAM = "accountType";
    public static final String UNIFIED = "UNIFIED";
    public static final String LINEAR = "linear";
    public static final String USDT = "USDT";
    public static final String FILLED_STATUS = "Filled";
    public static final String MARKET_ORDER_TYPE = "Market";
    public static final String LIMIT_ORDER_TYPE = "Limit";
    public static final String SIDE_KEY = "side";
    public static final String QUANTITY_KEY = "qty";
    public static final String ORDER_LINK_ID_KEY = "orderLinkId";
    public static final String REDUCE_ONLY_KEY = "reduceOnly";
    public static final String ORDER_TYPE_KEY = "orderType";
    public static final String TRIGGER_PRICE_KEY = "triggerPrice";
    public static final String TRIGGER_DIRECTION_KEY = "triggerDirection";
    public static final String PRICE_KEY = "price";
    public static final String TIME_IN_FORCE_KEY = "timeInForce";
    public static final String RESULT_KEY = "result";
    public static final String RET_CODE_KEY = "retCode";
    public static final String RET_MSG_KEY = "retMsg";
    public static final String ORDER_ID_KEY = "orderId";
    public static final String TOTAL_AVAILABLE_BALANCE_KEY = "totalAvailableBalance";
    public static final String LIST_KEY = "list";
    public static final String COIN_KEY = "coin";
    public static final String AVAILABLE_TO_WITHDRAW_KEY = "availableToWithdraw";
    public static final String WALLET_BALANCE_KEY = "walletBalance";
    public static final String ORDER_STATUS_KEY = "orderStatus";
    public static final String CUM_EXEC_QTY_KEY = "cumExecQty";
    public static final String AVG_PRICE_KEY = "avgPrice";
    public static final String CUM_EXEC_FEE_KEY = "cumExecFee";
    public static final String BUY_SIDE = "Buy";
    public static final String SELL_SIDE = "Sell";
    public static final String EMPTY_BYBIT_RESPONSE = "Empty response from Bybit";
    public static final String MISSING_INSTRUMENT_CONFIG = "No active instrument configuration found for pair ";
    public static final String UNSUPPORTED_ORDER_TYPE = "Unsupported Bybit order type: ";
    public static final String EMPTY_ENDPOINT_RESPONSE_SUFFIX = " returned an empty response";
    public static final String ENDPOINT_FAILURE_SEPARATOR = " failed: ";
    public static final String JSON_SERIALIZATION_ERROR = "Failed to serialize Bybit request";
    public static final String QUERY_SEPARATOR = "&";
    public static final String QUERY_EQUALS = "=";
    public static final String QUESTION_MARK = "?";

    private final TradingEngineProperties properties;
    private final JsonMapper jsonMapper;
    private final RuntimeConfigPort runtimeConfigPort;
    private final RestClient liveRestClient;
    private final RestClient demoRestClient;

    public BybitBrokerExecutionAdapter(TradingEngineProperties properties,
                                       JsonMapper jsonMapper,
                                       RuntimeConfigPort runtimeConfigPort) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.runtimeConfigPort = runtimeConfigPort;
        this.liveRestClient = RestClient.builder().baseUrl(properties.getBybitLiveBaseUrl()).build();
        this.demoRestClient = RestClient.builder().baseUrl(properties.getBybitDemoBaseUrl()).build();
    }

    @Override
    public BrokerCode brokerCode() {
        return BrokerCode.BYBIT;
    }

    @Override
    public BigDecimal getAvailableBalance(DecryptedBrokerCredentials credentials,
                                          long instrumentPairId) {
        String queryString = ACCOUNT_TYPE_PARAM + QUERY_EQUALS + UNIFIED;
        JsonNode response = signedGet(credentials, BybitV5Endpoint.GET_WALLET, queryString);
        if (response == null) {
            return BigDecimal.ZERO;
        }
        JsonNode result = response.path(RESULT_KEY);
        JsonNode totalAvailable = result.path(TOTAL_AVAILABLE_BALANCE_KEY);
        if (!totalAvailable.isMissingNode() && !totalAvailable.isNull()) {
            return decimal(totalAvailable);
        }
        JsonNode list = result.path(LIST_KEY);
        if (!list.isArray()) {
            return BigDecimal.ZERO;
        }
        for (JsonNode wallet : list) {
            JsonNode walletAvailable = wallet.path(TOTAL_AVAILABLE_BALANCE_KEY);
            if (!walletAvailable.isMissingNode() && !walletAvailable.isNull()) {
                return decimal(walletAvailable);
            }
            JsonNode coins = wallet.path(COIN_KEY);
            if (!coins.isArray()) {
                continue;
            }
            for (JsonNode coin : coins) {
                if (USDT.equals(coin.path(COIN_KEY).asString())) {
                    JsonNode available = coin.path(AVAILABLE_TO_WITHDRAW_KEY);
                    if (available.isMissingNode() || available.isNull()) {
                        available = coin.path(WALLET_BALANCE_KEY);
                    }
                    return decimal(available);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    @Override
    public OrderExecutionReceipt placeOrder(DecryptedBrokerCredentials credentials,
                                            PlaceOrderCommand command) {
        String category = category(credentials);
        ObjectNode body = jsonMapper.createObjectNode();
        body.put(CATEGORY_PARAM, category);
        body.put(SYMBOL_PARAM, command.symbol());
        body.put(SIDE_KEY, bybitSide(command.side()));
        body.put(QUANTITY_KEY, command.quantity().toPlainString());
        body.put(ORDER_LINK_ID_KEY, command.clientOrderId());

        switch (command.orderType()) {
            case MARKET -> body.put(ORDER_TYPE_KEY, MARKET_ORDER_TYPE);
            case STOP -> {
                body.put(ORDER_TYPE_KEY, MARKET_ORDER_TYPE);
                body.put(TRIGGER_PRICE_KEY, command.stopPrice().toPlainString());
                body.put(TRIGGER_DIRECTION_KEY, command.side() == OrderSide.SELL ? 2 : 1);
                body.put(REDUCE_ONLY_KEY, true);
            }
            case LIMIT -> {
                body.put(ORDER_TYPE_KEY, LIMIT_ORDER_TYPE);
                body.put(PRICE_KEY, command.price().toPlainString());
                body.put(TIME_IN_FORCE_KEY, command.timeInForce().name());
            }
            case STOP_LIMIT -> throw new IllegalArgumentException(UNSUPPORTED_ORDER_TYPE + command.orderType());
        }
        if (command.orderType() != OrderType.STOP && command.reduceOnly()) {
            body.put(REDUCE_ONLY_KEY, true);
        }

        JsonNode response = signedPost(credentials, BybitV5Endpoint.CREATE_ORDER, serialize(body));
        if (response == null) {
            return rejected(EMPTY_BYBIT_RESPONSE);
        }
        if (response.path(RET_CODE_KEY).asInt(-1) != 0) {
            return rejected(response.path(RET_MSG_KEY).asString());
        }
        String orderId = response.path(RESULT_KEY).path(ORDER_ID_KEY).asString();
        if (command.orderType() == OrderType.STOP) {
            return new OrderExecutionReceipt(orderId, OrderStatus.OPEN, BigDecimal.ZERO,
                    null, BigDecimal.ZERO, null, null);
        }
        if (command.orderType() == OrderType.MARKET) {
            JsonNode executed = queryExecutedOrder(credentials, category, command.symbol(), orderId);
            if (executed != null && FILLED_STATUS.equalsIgnoreCase(executed.path(ORDER_STATUS_KEY).asString())) {
                return new OrderExecutionReceipt(orderId, OrderStatus.FILLED,
                        decimal(executed.path(CUM_EXEC_QTY_KEY)), decimal(executed.path(AVG_PRICE_KEY)),
                        decimal(executed.path(CUM_EXEC_FEE_KEY)), USDT, null);
            }
        }
        return new OrderExecutionReceipt(orderId, OrderStatus.OPEN, BigDecimal.ZERO,
                null, BigDecimal.ZERO, null, null);
    }

    @Override
    public void amendStopLoss(DecryptedBrokerCredentials credentials,
                              AmendStopLossCommand command) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put(CATEGORY_PARAM, category(credentials));
        body.put(SYMBOL_PARAM, command.symbol());
        body.put(ORDER_ID_PARAM, command.exchangeOrderId());
        body.put(TRIGGER_PRICE_KEY, command.newStopPrice().toPlainString());
        requireSuccess(signedPost(credentials, BybitV5Endpoint.AMEND_ORDER, serialize(body)),
                BybitV5Endpoint.AMEND_ORDER);
    }

    @Override
    public void cancelOrder(DecryptedBrokerCredentials credentials,
                            long instrumentPairId,
                            String exchangeOrderId) {
        String symbol = runtimeConfigPort.loadActiveConfigs().stream()
                .filter(config -> config.instrumentPairId() == instrumentPairId)
                .map(AlgotradingRuntimeConfig::symbol)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(MISSING_INSTRUMENT_CONFIG + instrumentPairId));
        ObjectNode body = jsonMapper.createObjectNode();
        body.put(CATEGORY_PARAM, category(credentials));
        body.put(SYMBOL_PARAM, symbol);
        body.put(ORDER_ID_PARAM, exchangeOrderId);
        requireSuccess(signedPost(credentials, BybitV5Endpoint.CANCEL_ORDER, serialize(body)),
                BybitV5Endpoint.CANCEL_ORDER);
    }

    private JsonNode signedGet(DecryptedBrokerCredentials credentials,
                               BybitV5Endpoint endpoint,
                               String queryString) {
        long timestamp = Instant.now().toEpochMilli();
        String signature = BybitV5Signer.sign(credentials.apiKey(), credentials.apiSecret(),
                timestamp, properties.getBybitRecvWindowMillis(), queryString);
        URI uri = URI.create(endpoint.path() + QUESTION_MARK + queryString);
        return restClient(credentials).get()
                .uri(uri)
                .header(X_BAPI_API_KEY, credentials.apiKey())
                .header(X_BAPI_TIMESTAMP, Long.toString(timestamp))
                .header(X_BAPI_RECV_WINDOW, Long.toString(properties.getBybitRecvWindowMillis()))
                .header(X_BAPI_SIGN, signature)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode signedPost(DecryptedBrokerCredentials credentials,
                                BybitV5Endpoint endpoint,
                                String jsonBody) {
        long timestamp = Instant.now().toEpochMilli();
        String signature = BybitV5Signer.sign(credentials.apiKey(), credentials.apiSecret(),
                timestamp, properties.getBybitRecvWindowMillis(), jsonBody);
        return restClient(credentials).post()
                .uri(endpoint.path())
                .header(X_BAPI_API_KEY, credentials.apiKey())
                .header(X_BAPI_TIMESTAMP, Long.toString(timestamp))
                .header(X_BAPI_RECV_WINDOW, Long.toString(properties.getBybitRecvWindowMillis()))
                .header(X_BAPI_SIGN, signature)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(jsonBody.getBytes(StandardCharsets.UTF_8))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode queryExecutedOrder(DecryptedBrokerCredentials credentials,
                                        String category,
                                        String symbol,
                                        String orderId) {
        String queryString = CATEGORY_PARAM + QUERY_EQUALS + category + QUERY_SEPARATOR
                + SYMBOL_PARAM + QUERY_EQUALS + symbol + QUERY_SEPARATOR
                + ORDER_ID_PARAM + QUERY_EQUALS + orderId;
        JsonNode realtime = signedGet(credentials, BybitV5Endpoint.REALTIME_ORDER, queryString);
        JsonNode row = firstListRow(realtime);
        if (row != null) {
            return row;
        }
        return firstListRow(signedGet(credentials, BybitV5Endpoint.HISTORY_ORDER, queryString));
    }

    private static JsonNode firstListRow(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode list = response.path(RESULT_KEY).path(LIST_KEY);
        return list.isArray() && !list.isEmpty() ? list.get(0) : null;
    }

    private RestClient restClient(DecryptedBrokerCredentials credentials) {
        return credentials.paper() ? demoRestClient : liveRestClient;
    }

    private static String category(DecryptedBrokerCredentials credentials) {
        return credentials.marketCategory() == null || credentials.marketCategory().isBlank()
                ? LINEAR
                : credentials.marketCategory();
    }

    private String serialize(ObjectNode body) {
        try {
            return jsonMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw new IllegalStateException(JSON_SERIALIZATION_ERROR, exception);
        }
    }

    private static String bybitSide(OrderSide side) {
        return switch (side) {
            case BUY -> BUY_SIDE;
            case SELL -> SELL_SIDE;
        };
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        String value = node.asString();
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static void requireSuccess(JsonNode response, BybitV5Endpoint endpoint) {
        if (response == null) {
            throw new IllegalStateException(endpoint + EMPTY_ENDPOINT_RESPONSE_SUFFIX);
        }
        if (response.path(RET_CODE_KEY).asInt(-1) != 0) {
            throw new IllegalStateException(endpoint + ENDPOINT_FAILURE_SEPARATOR
                    + response.path(RET_MSG_KEY).asString());
        }
    }

    private static OrderExecutionReceipt rejected(String reason) {
        return new OrderExecutionReceipt(null, OrderStatus.REJECTED, BigDecimal.ZERO,
                null, BigDecimal.ZERO, null, reason);
    }
}

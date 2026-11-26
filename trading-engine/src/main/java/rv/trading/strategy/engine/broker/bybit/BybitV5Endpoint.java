package rv.trading.strategy.engine.broker.bybit;

public enum BybitV5Endpoint {
    GET_WALLET("/v5/account/wallet-balance"),
    CREATE_ORDER("/v5/order/create"),
    REALTIME_ORDER("/v5/order/realtime"),
    HISTORY_ORDER("/v5/order/history"),
    AMEND_ORDER("/v5/order/amend"),
    CANCEL_ORDER("/v5/order/cancel");

    private final String path;

    BybitV5Endpoint(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}

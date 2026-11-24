-- ============================================================================
-- Strategy Algorithmic Trading - Initial Schema (PostgreSQL 16+)
-- Migration Owner: portal-api (Flyway)
-- ============================================================================

-- ============================================================================
-- SECTION 1: OWNED BY portal-api
-- ============================================================================

-- OWNER: portal-api
CREATE TABLE users (
                       id            BIGSERIAL    PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role          VARCHAR(32)  NOT NULL DEFAULT 'ROLE_USER',
                       enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       CONSTRAINT chk_users_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

-- OWNER: portal-api
CREATE TABLE broker (
                        id   BIGSERIAL    PRIMARY KEY,
                        code VARCHAR(32)  NOT NULL UNIQUE,
                        name VARCHAR(100) NOT NULL
);

-- OWNER: portal-api
CREATE TABLE broker_client (
                               id                   BIGSERIAL    PRIMARY KEY,
                               user_id              BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               broker_id            BIGINT       NOT NULL REFERENCES broker(id),
                               label                VARCHAR(100) NOT NULL,
                               external_account_id  VARCHAR(64),
                               encrypted_api_key    TEXT         NOT NULL,
                               encrypted_api_secret TEXT         NOT NULL,
                               is_paper             BOOLEAN      NOT NULL DEFAULT FALSE,
                               status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
                               created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               CONSTRAINT chk_broker_client_status CHECK (status IN ('ACTIVE', 'DISABLED', 'INVALID_CREDENTIALS'))
);

CREATE INDEX idx_broker_client_user_id ON broker_client (user_id);

-- OWNER: portal-api
CREATE TABLE instrument (
                            id         BIGSERIAL    PRIMARY KEY,
                            symbol     VARCHAR(32)  NOT NULL UNIQUE,
                            name       VARCHAR(128) NOT NULL,
                            asset_type VARCHAR(24)  NOT NULL,
                            CONSTRAINT chk_instrument_asset_type CHECK (asset_type IN ('CRYPTO', 'STOCK', 'ETF', 'FIAT'))
);

-- OWNER: portal-api
CREATE TABLE instrument_pair (
                                 id                   BIGSERIAL      PRIMARY KEY,
                                 broker_id            BIGINT         NOT NULL REFERENCES broker(id),
                                 base_instrument_id   BIGINT         NOT NULL REFERENCES instrument(id),
                                 quote_instrument_id  BIGINT         NOT NULL REFERENCES instrument(id),
                                 symbol               VARCHAR(64)    NOT NULL,
                                 market_type          VARCHAR(32)    NOT NULL,
                                 external_contract_id VARCHAR(64),
                                 primary_exchange     VARCHAR(32),
                                 tick_size            NUMERIC(20, 8) NOT NULL,
                                 step_size            NUMERIC(20, 8) NOT NULL,
                                 active               BOOLEAN        NOT NULL DEFAULT TRUE,
                                 CONSTRAINT uq_instrument_pair_broker_symbol_market UNIQUE (broker_id, symbol, market_type),
                                 CONSTRAINT chk_instrument_pair_market_type CHECK (market_type IN ('SPOT', 'LINEAR_PERPETUAL', 'STOCK')),
                                 CONSTRAINT chk_instrument_pair_sizes CHECK (tick_size > 0 AND step_size > 0)
);

-- OWNER: portal-api
CREATE TABLE timeframe (
                           id               BIGSERIAL   PRIMARY KEY,
                           code             VARCHAR(16) NOT NULL UNIQUE,
                           duration_seconds INTEGER     NOT NULL,
                           CONSTRAINT chk_timeframe_code CHECK (code IN ('M1', 'M5', 'M15', 'H1', 'H4', 'D1', 'W1')),
                           CONSTRAINT chk_timeframe_duration CHECK (duration_seconds > 0)
);

-- OWNER: portal-api
CREATE TABLE indicator (
                           id          BIGSERIAL    PRIMARY KEY,
                           code        VARCHAR(32)  NOT NULL UNIQUE,
                           name        VARCHAR(100) NOT NULL,
                           description TEXT
);

-- OWNER: portal-api
CREATE TABLE indicator_parameter (
                                     id            BIGSERIAL    PRIMARY KEY,
                                     indicator_id  BIGINT       NOT NULL REFERENCES indicator(id) ON DELETE CASCADE,
                                     param_key     VARCHAR(64)  NOT NULL,
                                     data_type     VARCHAR(24)  NOT NULL,
                                     default_value VARCHAR(64)  NOT NULL,
                                     description   VARCHAR(255),
                                     CONSTRAINT uq_indicator_parameter_key UNIQUE (indicator_id, param_key),
                                     CONSTRAINT chk_indicator_parameter_type CHECK (data_type IN ('INT', 'DECIMAL', 'BOOLEAN'))
);

-- OWNER: portal-api
CREATE TABLE strategy (
                          id          BIGSERIAL    PRIMARY KEY,
                          user_id     BIGINT       REFERENCES users(id) ON DELETE CASCADE,
                          name        VARCHAR(128) NOT NULL,
                          description TEXT,
                          created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                          updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_strategy_user_id ON strategy (user_id);

-- OWNER: portal-api
CREATE TABLE strategy_indicator (
                                    id           BIGSERIAL   PRIMARY KEY,
                                    strategy_id  BIGINT      NOT NULL REFERENCES strategy(id) ON DELETE CASCADE,
                                    indicator_id BIGINT      NOT NULL REFERENCES indicator(id),
                                    alias        VARCHAR(64) NOT NULL,
                                    role         VARCHAR(16) NOT NULL,
                                    CONSTRAINT uq_strategy_indicator_alias UNIQUE (strategy_id, alias),
                                    CONSTRAINT chk_strategy_indicator_role CHECK (role IN ('TRIGGER', 'FILTER'))
);

CREATE INDEX idx_strategy_indicator_strategy_id ON strategy_indicator (strategy_id);

-- OWNER: portal-api
CREATE TABLE algotrading (
                             id                 BIGSERIAL      PRIMARY KEY,
                             broker_client_id   BIGINT         NOT NULL REFERENCES broker_client(id),
                             strategy_id        BIGINT         NOT NULL REFERENCES strategy(id),
                             instrument_pair_id BIGINT         NOT NULL REFERENCES instrument_pair(id),
                             timeframe_id       BIGINT         NOT NULL REFERENCES timeframe(id),
                             direction_mode     VARCHAR(16)    NOT NULL DEFAULT 'BOTH',
                             indicator_params   JSONB          NOT NULL DEFAULT '{}'::jsonb,
                             order_volume       NUMERIC(20, 8) NOT NULL,
                             status             VARCHAR(16)    NOT NULL DEFAULT 'STOPPED',
                             error_message      TEXT,
                             version            BIGINT         NOT NULL DEFAULT 0,
                             created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                             updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                             CONSTRAINT chk_algotrading_direction CHECK (direction_mode IN ('LONG_ONLY', 'SHORT_ONLY', 'BOTH')),
                             CONSTRAINT chk_algotrading_status CHECK (status IN ('ACTIVE', 'STOPPED', 'ERROR')),
                             CONSTRAINT chk_algotrading_volume CHECK (order_volume > 0)
);

-- Only one ACTIVE bot allowed per (broker_client_id, instrument_pair_id)
CREATE UNIQUE INDEX uq_algotrading_active_client_pair
    ON algotrading (broker_client_id, instrument_pair_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_algotrading_status ON algotrading (status);
CREATE INDEX idx_algotrading_pair_status ON algotrading (instrument_pair_id, status);

-- ============================================================================
-- SECTION 2: OWNED BY market-collector
-- ============================================================================

-- OWNER: market-collector
CREATE TABLE candle (
                        id                 BIGSERIAL      PRIMARY KEY,
                        instrument_pair_id BIGINT         NOT NULL REFERENCES instrument_pair(id),
                        timeframe_id       BIGINT         NOT NULL REFERENCES timeframe(id),
                        open_time          TIMESTAMPTZ    NOT NULL,
                        open               NUMERIC(20, 8) NOT NULL,
                        high               NUMERIC(20, 8) NOT NULL,
                        low                NUMERIC(20, 8) NOT NULL,
                        close              NUMERIC(20, 8) NOT NULL,
                        volume             NUMERIC(28, 8) NOT NULL DEFAULT 0,
                        closed             BOOLEAN        NOT NULL DEFAULT TRUE,
                        CONSTRAINT uq_candle_pair_tf_open_time UNIQUE (instrument_pair_id, timeframe_id, open_time)
);

CREATE INDEX idx_candle_warmup_lookup
    ON candle (instrument_pair_id, timeframe_id, open_time DESC);

-- ============================================================================
-- SECTION 3: OWNED BY trade-engine
-- ============================================================================

-- OWNER: trade-engine
CREATE TABLE signal (
                        id                 BIGSERIAL      PRIMARY KEY,
                        algotrading_id     BIGINT         NOT NULL REFERENCES algotrading(id),
                        candle_open_time   TIMESTAMPTZ    NOT NULL,
                        signal_type        VARCHAR(24)    NOT NULL,
                        status             VARCHAR(24)    NOT NULL DEFAULT 'DETECTED',
                        trigger_price      NUMERIC(20, 8) NOT NULL,
                        indicator_snapshot JSONB          NOT NULL DEFAULT '{}'::jsonb,
                        error_message      TEXT,
                        created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                        CONSTRAINT uq_signal_algo_candle_type UNIQUE (algotrading_id, candle_open_time, signal_type),
                        CONSTRAINT chk_signal_type CHECK (signal_type IN ('BUY_OPEN', 'BUY_CLOSE', 'SELL_OPEN', 'SELL_CLOSE')),
                        CONSTRAINT chk_signal_status CHECK (status IN ('DETECTED', 'EXECUTED', 'IGNORED', 'FAILED'))
);

CREATE INDEX idx_signal_algotrading_created ON signal (algotrading_id, created_at DESC);

-- OWNER: trade-engine
CREATE TABLE position (
                          id                 BIGSERIAL      PRIMARY KEY,
                          broker_client_id   BIGINT         NOT NULL REFERENCES broker_client(id),
                          instrument_pair_id BIGINT         NOT NULL REFERENCES instrument_pair(id),
                          side               VARCHAR(16)    NOT NULL,
                          status             VARCHAR(16)    NOT NULL DEFAULT 'OPEN',
                          quantity           NUMERIC(20, 8) NOT NULL,
                          avg_entry_price    NUMERIC(20, 8) NOT NULL,
                          current_stop_loss  NUMERIC(20, 8),
                          realized_pnl       NUMERIC(20, 8) NOT NULL DEFAULT 0,
                          total_commission   NUMERIC(20, 8) NOT NULL DEFAULT 0,
                          currency           VARCHAR(16)    NOT NULL,
                          version            BIGINT         NOT NULL DEFAULT 0,
                          opened_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                          closed_at          TIMESTAMPTZ,
                          CONSTRAINT chk_position_side CHECK (side IN ('LONG', 'SHORT')),
                          CONSTRAINT chk_position_status CHECK (status IN ('OPEN', 'CLOSED')),
                          CONSTRAINT chk_position_quantity CHECK (quantity >= 0)
);

-- Netting position invariant: at most one OPEN position per (broker_client_id, instrument_pair_id)
CREATE UNIQUE INDEX uq_position_open_client_pair
    ON position (broker_client_id, instrument_pair_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_position_client_opened ON position (broker_client_id, opened_at DESC);

-- OWNER: trade-engine
CREATE TABLE orders (
                        id                  BIGSERIAL      PRIMARY KEY,
                        broker_client_id    BIGINT         NOT NULL REFERENCES broker_client(id),
                        instrument_pair_id  BIGINT         NOT NULL REFERENCES instrument_pair(id),
                        position_id         BIGINT         REFERENCES position(id),
                        algotrading_id      BIGINT         REFERENCES algotrading(id),
                        signal_id           BIGINT         REFERENCES signal(id),
                        parent_order_id     BIGINT         REFERENCES orders(id),
                        external_order_id   VARCHAR(64),
                        client_order_id     VARCHAR(64)    NOT NULL UNIQUE,
                        side                VARCHAR(16)    NOT NULL,
                        order_type          VARCHAR(16)    NOT NULL,
                        time_in_force       VARCHAR(16)    NOT NULL,
                        outside_rth         BOOLEAN        NOT NULL DEFAULT FALSE,
                        quantity            NUMERIC(20, 8) NOT NULL,
                        filled_quantity     NUMERIC(20, 8) NOT NULL DEFAULT 0,
                        price               NUMERIC(20, 8),
                        stop_price          NUMERIC(20, 8),
                        avg_fill_price      NUMERIC(20, 8),
                        commission          NUMERIC(20, 8) NOT NULL DEFAULT 0,
                        commission_currency VARCHAR(16),
                        status              VARCHAR(24)    NOT NULL,
                        reject_reason       TEXT,
                        version             BIGINT         NOT NULL DEFAULT 0,
                        created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                        updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                        CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL')),
                        CONSTRAINT chk_orders_type CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT')),
                        CONSTRAINT chk_orders_tif CHECK (time_in_force IN ('GTC', 'IOC', 'FOK', 'DAY')),
                        CONSTRAINT chk_orders_status CHECK (status IN (
                                                                       'PENDING_SUBMIT', 'PRE_SUBMITTED', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED'
                            )),
                        CONSTRAINT chk_orders_quantity CHECK (quantity > 0 AND filled_quantity >= 0)
);

CREATE INDEX idx_orders_client_pair_status ON orders (broker_client_id, instrument_pair_id, status);
CREATE INDEX idx_orders_client_created ON orders (broker_client_id, created_at DESC);
CREATE INDEX idx_orders_position_id ON orders (position_id);
CREATE INDEX idx_orders_external_id ON orders (broker_client_id, external_order_id);

-- ============================================================================
-- SECTION 4: INITIAL REFERENCE DATA (SEED)
-- ============================================================================

INSERT INTO broker (code, name) VALUES
                                    ('BYBIT', 'Bybit Exchange'),
                                    ('IBKR', 'Interactive Brokers');

INSERT INTO timeframe (code, duration_seconds) VALUES
                                                   ('M1',  60),
                                                   ('M5',  300),
                                                   ('M15', 900),
                                                   ('H1',  3600),
                                                   ('H4',  14400),
                                                   ('D1',  86400),
                                                   ('W1',  604800);

INSERT INTO indicator (code, name, description) VALUES
                                                    ('MACD', 'Moving Average Convergence Divergence', 'Trend-following momentum indicator based on fast/slow/signal EMAs'),
                                                    ('SAR',  'Parabolic SAR',                         'Stop and Reverse trailing trend and stop-loss indicator');

INSERT INTO indicator_parameter (indicator_id, param_key, data_type, default_value, description)
SELECT id, 'fastPeriod', 'INT', '12', 'Fast EMA period' FROM indicator WHERE code = 'MACD'
UNION ALL
SELECT id, 'slowPeriod', 'INT', '26', 'Slow EMA period' FROM indicator WHERE code = 'MACD'
UNION ALL
SELECT id, 'signalPeriod', 'INT', '9', 'Signal line EMA period' FROM indicator WHERE code = 'MACD'
UNION ALL
SELECT id, 'step', 'DECIMAL', '0.02', 'Acceleration factor step' FROM indicator WHERE code = 'SAR'
UNION ALL
SELECT id, 'maxStep', 'DECIMAL', '0.20', 'Maximum acceleration factor' FROM indicator WHERE code = 'SAR';
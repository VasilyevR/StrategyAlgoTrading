-- ============================================================================
-- V2: Strategy Params, Exit Modes, Selective Sharing & Dual SMA Example
-- ============================================================================

-- 1. Exit mode on strategy
ALTER TABLE strategy
    ADD COLUMN exit_mode VARCHAR(24) NOT NULL DEFAULT 'SIGNAL_MARKET';

ALTER TABLE strategy
    ADD CONSTRAINT chk_strategy_exit_mode
        CHECK (exit_mode IN ('SIGNAL_MARKET', 'TRAILING_STOP', 'HYBRID'));

-- 2. Move configured indicator params into strategy_indicator & add TRAILING_STOP role
ALTER TABLE strategy_indicator
    DROP CONSTRAINT chk_strategy_indicator_role;

ALTER TABLE strategy_indicator
    ADD CONSTRAINT chk_strategy_indicator_role
        CHECK (role IN ('TRIGGER', 'FILTER', 'TRAILING_STOP'));

ALTER TABLE strategy_indicator
    ADD COLUMN params JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE algotrading
    DROP COLUMN indicator_params;

-- 3. Selective sharing of private strategies
-- OWNER: portal-api
CREATE TABLE strategy_share (
                                id          BIGSERIAL   PRIMARY KEY,
                                strategy_id BIGINT      NOT NULL REFERENCES strategy(id) ON DELETE CASCADE,
                                user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                allow_copy  BOOLEAN     NOT NULL DEFAULT FALSE,
                                granted_by  BIGINT      NOT NULL REFERENCES users(id),
                                created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                CONSTRAINT uq_strategy_share_strategy_user UNIQUE (strategy_id, user_id)
);

CREATE INDEX idx_strategy_share_user_id ON strategy_share (user_id);

-- 4. Register SMA_CROSS indicator & seed public Dual SMA example strategy
INSERT INTO indicator (code, name, description) VALUES
    ('SMA_CROSS', 'Dual SMA Crossover', 'Simple moving average crossover (fast SMA vs slow SMA)');

INSERT INTO indicator_parameter (indicator_id, param_key, data_type, default_value, description)
SELECT id, 'fastPeriod', 'INT', '9', 'Fast SMA period' FROM indicator WHERE code = 'SMA_CROSS'
UNION ALL
SELECT id, 'slowPeriod', 'INT', '21', 'Slow SMA period' FROM indicator WHERE code = 'SMA_CROSS';

INSERT INTO strategy (user_id, name, description, exit_mode) VALUES
    (NULL, 'Dual SMA Crossover (Example)', 'Public example strategy: enter on fast SMA crossing above slow SMA, exit via market order on opposite crossover.', 'SIGNAL_MARKET');

INSERT INTO strategy_indicator (strategy_id, indicator_id, alias, role, params)
SELECT s.id, i.id, 'sma_cross_main', 'TRIGGER', '{"fastPeriod": 9, "slowPeriod": 21}'::jsonb
FROM strategy s
         JOIN indicator i ON i.code = 'SMA_CROSS'
WHERE s.user_id IS NULL AND s.name = 'Dual SMA Crossover (Example)';
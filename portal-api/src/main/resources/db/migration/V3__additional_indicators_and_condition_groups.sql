ALTER TABLE strategy_indicator
    ADD COLUMN condition_group INT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_strategy_indicator_condition_group CHECK (condition_group >= 1);

INSERT INTO indicator (code, name, description) VALUES
    ('CMF', 'Chaikin Money Flow', 'Volume-weighted accumulation and distribution indicator'),
    ('AO', 'Awesome Oscillator', 'Difference between fast and slow median-price averages'),
    ('ULTOSC', 'Ultimate Oscillator', 'Multi-period buying pressure oscillator'),
    ('CCI', 'Commodity Channel Index', 'Price deviation from its statistical mean'),
    ('ROC', 'Rate of Change', 'Percentage change in price over a lookback period'),
    ('KAMA', 'Kaufman Adaptive Moving Average', 'Adaptive moving average based on market efficiency'),
    ('STOCH', 'Stochastic Oscillator', 'Momentum oscillator comparing close price to its recent range'),
    ('STOCH_RVI', 'Stochastic Relative Vigor Index', 'Min-max normalized Ehlers Relative Vigor Index');

INSERT INTO indicator_parameter (indicator_id, param_key, data_type, default_value, description)
SELECT id, 'period1', 'INT', '7', 'Short period' FROM indicator WHERE code = 'ULTOSC'
UNION ALL
SELECT id, 'period2', 'INT', '14', 'Middle period' FROM indicator WHERE code = 'ULTOSC'
UNION ALL
SELECT id, 'period3', 'INT', '28', 'Long period' FROM indicator WHERE code = 'ULTOSC'
UNION ALL
SELECT id, 'lowThreshold', 'DECIMAL', '30.0', 'Oversold threshold' FROM indicator WHERE code = 'ULTOSC'
UNION ALL
SELECT id, 'highThreshold', 'DECIMAL', '70.0', 'Overbought threshold' FROM indicator WHERE code = 'ULTOSC'
UNION ALL
SELECT id, 'period', 'INT', '20', 'Money flow lookback period' FROM indicator WHERE code = 'CMF'
UNION ALL
SELECT id, 'buyThreshold', 'DECIMAL', '0.0', 'Bullish money flow threshold' FROM indicator WHERE code = 'CMF'
UNION ALL
SELECT id, 'sellThreshold', 'DECIMAL', '0.0', 'Bearish money flow threshold' FROM indicator WHERE code = 'CMF'
UNION ALL
SELECT id, 'fastPeriod', 'INT', '5', 'Fast median-price average period' FROM indicator WHERE code = 'AO'
UNION ALL
SELECT id, 'slowPeriod', 'INT', '34', 'Slow median-price average period' FROM indicator WHERE code = 'AO'
UNION ALL
SELECT id, 'period', 'INT', '20', 'Lookback period' FROM indicator WHERE code = 'CCI'
UNION ALL
SELECT id, 'oversold', 'DECIMAL', '-100.0', 'Oversold threshold' FROM indicator WHERE code = 'CCI'
UNION ALL
SELECT id, 'overbought', 'DECIMAL', '100.0', 'Overbought threshold' FROM indicator WHERE code = 'CCI'
UNION ALL
SELECT id, 'period', 'INT', '12', 'Lookback period' FROM indicator WHERE code = 'ROC'
UNION ALL
SELECT id, 'minThreshold', 'DECIMAL', '-1000.0', 'Minimum ROC threshold' FROM indicator WHERE code = 'ROC'
UNION ALL
SELECT id, 'maxThreshold', 'DECIMAL', '1000.0', 'Maximum ROC threshold' FROM indicator WHERE code = 'ROC'
UNION ALL
SELECT id, 'erPeriod', 'INT', '10', 'Efficiency ratio period' FROM indicator WHERE code = 'KAMA'
UNION ALL
SELECT id, 'fastPeriod', 'INT', '2', 'Fast smoothing period' FROM indicator WHERE code = 'KAMA'
UNION ALL
SELECT id, 'slowPeriod', 'INT', '30', 'Slow smoothing period' FROM indicator WHERE code = 'KAMA'
UNION ALL
SELECT id, 'kPeriod', 'INT', '14', 'Stochastic %K period' FROM indicator WHERE code = 'STOCH'
UNION ALL
SELECT id, 'dPeriod', 'INT', '3', 'Stochastic %D period' FROM indicator WHERE code = 'STOCH'
UNION ALL
SELECT id, 'oversold', 'DECIMAL', '20.0', 'Oversold threshold' FROM indicator WHERE code = 'STOCH'
UNION ALL
SELECT id, 'overbought', 'DECIMAL', '80.0', 'Overbought threshold' FROM indicator WHERE code = 'STOCH'
UNION ALL
SELECT id, 'length', 'INT', '10', 'RVI and stochastic lookback length' FROM indicator WHERE code = 'STOCH_RVI'
UNION ALL
SELECT id, 'oversold', 'DECIMAL', '20.0', 'Oversold threshold' FROM indicator WHERE code = 'STOCH_RVI'
UNION ALL
SELECT id, 'overbought', 'DECIMAL', '80.0', 'Overbought threshold' FROM indicator WHERE code = 'STOCH_RVI';

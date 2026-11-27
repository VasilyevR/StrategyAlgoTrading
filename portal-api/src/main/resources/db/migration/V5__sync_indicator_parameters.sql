-- ============================================================================
-- V5: Sync SAR & CCI indicator_parameter keys with trading-engine evaluators
-- ============================================================================

-- 1. Allow STRING data_type for enum-backed parameters (mode, conditionType)
ALTER TABLE indicator_parameter
    DROP CONSTRAINT chk_indicator_parameter_type;

ALTER TABLE indicator_parameter
    ADD CONSTRAINT chk_indicator_parameter_type
        CHECK (data_type IN ('INT', 'DECIMAL', 'BOOLEAN', 'STRING'));

-- 2. Replace legacy SAR parameter keys (step, maxStep) with evaluator keys
DELETE FROM indicator_parameter
WHERE indicator_id = (SELECT id FROM indicator WHERE code = 'SAR')
  AND param_key IN ('step', 'maxStep');

INSERT INTO indicator_parameter (indicator_id, param_key, data_type, default_value, description)
SELECT id, 'accelerationStart', 'DECIMAL', '0.02', 'Initial acceleration factor' FROM indicator WHERE code = 'SAR'
UNION ALL
SELECT id, 'accelerationIncrement', 'DECIMAL', '0.02', 'Acceleration factor step increment' FROM indicator WHERE code = 'SAR'
UNION ALL
SELECT id, 'maxAcceleration', 'DECIMAL', '0.20', 'Maximum acceleration factor' FROM indicator WHERE code = 'SAR'
UNION ALL
SELECT id, 'mode', 'STRING', 'TREND', 'Signal mode (TREND or CONTRARIAN_EXTREME)' FROM indicator WHERE code = 'SAR'
ON CONFLICT (indicator_id, param_key) DO NOTHING;

-- 3. Add conditionType parameter for CCI (THRESHOLD reversal vs CROSS breakout)
INSERT INTO indicator_parameter (indicator_id, param_key, data_type, default_value, description)
SELECT id, 'conditionType', 'STRING', 'THRESHOLD', 'Trigger mode (THRESHOLD or CROSS)'
FROM indicator
WHERE code = 'CCI'
ON CONFLICT (indicator_id, param_key) DO NOTHING;

-- 4. Migrate existing strategy_indicator.params JSONB for SAR
UPDATE strategy_indicator si
SET params = (si.params - 'step' - 'maxStep')
    || jsonb_build_object(
                     'accelerationStart', COALESCE(si.params -> 'accelerationStart', si.params -> 'step', to_jsonb(0.02)),
                     'accelerationIncrement', COALESCE(si.params -> 'accelerationIncrement', si.params -> 'step', to_jsonb(0.02)),
                     'maxAcceleration', COALESCE(si.params -> 'maxAcceleration', si.params -> 'maxStep', to_jsonb(0.20))
       )
FROM indicator i
WHERE si.indicator_id = i.id
  AND i.code = 'SAR'
  AND (si.params ? 'step' OR si.params ? 'maxStep');
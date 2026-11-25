ALTER TABLE algotrading
    ADD COLUMN sizing_mode VARCHAR(24) NOT NULL DEFAULT 'FIXED_VOLUME',
    ADD CONSTRAINT chk_algotrading_sizing_mode
        CHECK (sizing_mode IN ('FIXED_VOLUME', 'RISK_PERCENT'));

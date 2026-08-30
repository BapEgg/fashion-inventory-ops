-- StockPilot V13: replaces the last two V12 comments that still carried an
-- untranslated English term where an established Korean gloss already exists
-- elsewhere in this schema -- "BASE" already reads "기준" on
-- sp_rebalance_scenario.scenario_type, and "Draft" already reads "이동 초안" on
-- sp_transfer_draft (table comment "재고 이동 초안"). No data, constraint, index,
-- or Java-visible behavior changes; V10/V11/V12 are not modified.

COMMENT ON COLUMN sp_approval_basis.recommended_base_quantity IS '추천 기준수량';
COMMENT ON COLUMN sp_approval_basis.already_approved_draft_quantity IS '출고 매장 기승인 활성 이동 초안 합계수량';

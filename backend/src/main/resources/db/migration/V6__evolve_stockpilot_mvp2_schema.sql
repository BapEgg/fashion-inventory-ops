-- StockPilot MVP-2 approved demo schema.
-- Every threshold/policy value is a versioned ASSUMPTION, not actual F&F policy
-- or a validated industry standard. Existing V1-V5 rows and audit history remain.

ALTER TABLE sp_product ADD (
    launch_date DATE,
    season_code VARCHAR2(40 CHAR),
    sales_status VARCHAR2(20 CHAR)
);

UPDATE sp_product
SET launch_date = DATE '2026-01-01',
    season_code = 'DEMO-2026',
    sales_status = 'ACTIVE';

ALTER TABLE sp_product MODIFY (
    launch_date NOT NULL,
    season_code NOT NULL,
    sales_status NOT NULL
);

ALTER TABLE sp_product ADD CONSTRAINT ck_sp_product_launch
    CHECK (launch_date = TRUNC(launch_date));
ALTER TABLE sp_product ADD CONSTRAINT ck_sp_product_status
    CHECK (sales_status IN ('PRELAUNCH', 'ACTIVE', 'CLEARANCE', 'ENDED'));

ALTER TABLE sp_store ADD (
    store_type VARCHAR2(20 CHAR),
    inventory_owner_code VARCHAR2(64 CHAR),
    transfer_zone VARCHAR2(40 CHAR)
);

UPDATE sp_store
SET store_type = 'DIRECT',
    inventory_owner_code = 'OWNER-DEMO-A',
    transfer_zone = 'DOMESTIC';

ALTER TABLE sp_store MODIFY (
    store_type NOT NULL,
    inventory_owner_code NOT NULL,
    transfer_zone NOT NULL
);

ALTER TABLE sp_store ADD CONSTRAINT ck_sp_store_type
    CHECK (store_type IN ('DIRECT', 'CONSIGNMENT', 'OTHER'));
ALTER TABLE sp_store ADD CONSTRAINT ck_sp_store_owner
    CHECK (LENGTH(TRIM(inventory_owner_code)) > 0);
ALTER TABLE sp_store ADD CONSTRAINT ck_sp_store_zone
    CHECK (LENGTH(TRIM(transfer_zone)) > 0);

ALTER TABLE sp_inventory_snapshot ADD (
    snapshot_at TIMESTAMP(6) WITH TIME ZONE,
    out_of_stock_flag CHAR(1 CHAR),
    input_snapshot_version VARCHAR2(64 CHAR)
);

UPDATE sp_inventory_snapshot
SET snapshot_at = FROM_TZ(CAST(snapshot_date AS TIMESTAMP), 'Asia/Seoul'),
    out_of_stock_flag = CASE
        WHEN on_hand_quantity - reserved_quantity = 0 THEN 'Y'
        ELSE 'N'
    END,
    input_snapshot_version = 'MVP-1-LEGACY';

ALTER TABLE sp_inventory_snapshot MODIFY (
    snapshot_at DEFAULT SYSTIMESTAMP NOT NULL,
    out_of_stock_flag DEFAULT 'N' NOT NULL,
    input_snapshot_version DEFAULT 'MVP-1-LEGACY' NOT NULL
);

ALTER TABLE sp_inventory_snapshot DROP CONSTRAINT uq_sp_inv_snapshot;
ALTER TABLE sp_inventory_snapshot ADD CONSTRAINT uq_sp_inv_snapshot
    UNIQUE (snapshot_date, store_id, sku_id, input_snapshot_version);
ALTER TABLE sp_inventory_snapshot ADD CONSTRAINT ck_sp_inv_oos
    CHECK (out_of_stock_flag IN ('Y', 'N'));
ALTER TABLE sp_inventory_snapshot ADD CONSTRAINT ck_sp_inv_input_ver
    CHECK (LENGTH(TRIM(input_snapshot_version)) > 0);

ALTER TABLE sp_daily_sale ADD (
    transaction_count NUMBER(10, 0),
    max_transaction_quantity NUMBER(10, 0),
    average_selling_price NUMBER(14, 2),
    input_snapshot_version VARCHAR2(64 CHAR)
);

UPDATE sp_daily_sale
SET transaction_count = CASE WHEN sold_quantity = 0 THEN 0 ELSE sold_quantity END,
    max_transaction_quantity = CASE WHEN sold_quantity = 0 THEN 0 ELSE 1 END,
    input_snapshot_version = 'MVP-1-LEGACY';

ALTER TABLE sp_daily_sale MODIFY (
    input_snapshot_version DEFAULT 'MVP-1-LEGACY' NOT NULL
);

ALTER TABLE sp_daily_sale DROP CONSTRAINT uq_sp_daily_sale;
ALTER TABLE sp_daily_sale ADD CONSTRAINT uq_sp_daily_sale
    UNIQUE (sales_date, store_id, sku_id, input_snapshot_version);
ALTER TABLE sp_daily_sale ADD CONSTRAINT ck_sp_sale_input_ver
    CHECK (LENGTH(TRIM(input_snapshot_version)) > 0);
ALTER TABLE sp_daily_sale ADD CONSTRAINT ck_sp_sale_mvp2_detail CHECK (
    input_snapshot_version = 'MVP-1-LEGACY'
    OR (
        transaction_count IS NOT NULL
        AND max_transaction_quantity IS NOT NULL
        AND average_selling_price IS NOT NULL
        AND average_selling_price >= 0
        AND (
            (sold_quantity = 0 AND transaction_count = 0 AND max_transaction_quantity = 0)
            OR (
                sold_quantity > 0
                AND transaction_count > 0
                AND max_transaction_quantity > 0
                AND transaction_count <= sold_quantity
                AND max_transaction_quantity <= sold_quantity
            )
        )
    )
);

ALTER TABLE sp_analysis_run ADD (
    input_snapshot_version VARCHAR2(64 CHAR)
        DEFAULT 'MVP-1-LEGACY' NOT NULL
);

ALTER TABLE sp_analysis_run DROP CONSTRAINT uq_sp_analysis_run;
ALTER TABLE sp_analysis_run ADD CONSTRAINT uq_sp_analysis_run
    UNIQUE (analysis_date, input_snapshot_version, rule_version);
ALTER TABLE sp_analysis_run ADD CONSTRAINT ck_sp_run_input_ver
    CHECK (LENGTH(TRIM(input_snapshot_version)) > 0);

ALTER TABLE sp_inventory_metric ADD (
    observable_day_count NUMBER(3, 0),
    active_week_count NUMBER(1, 0),
    sales_day_ratio NUMBER(7, 6),
    max_daily_sales NUMBER(10, 0),
    median_daily_sales NUMBER(18, 12),
    mad_daily_sales NUMBER(18, 12),
    max_transaction_quantity NUMBER(10, 0),
    primary_demand_signal_type VARCHAR2(30 CHAR),
    demand_confidence VARCHAR2(10 CHAR),
    low_demand_rate NUMBER(18, 12),
    base_demand_rate NUMBER(18, 12),
    high_demand_rate NUMBER(18, 12),
    projected_available NUMBER(12, 0),
    expected_shortage_quantity NUMBER(12, 0),
    inventory_exception_type VARCHAR2(30 CHAR),
    severity VARCHAR2(10 CHAR),
    calculation_version VARCHAR2(32 CHAR)
);

ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_mvp2_obs CHECK (
    (observable_day_count IS NULL OR observable_day_count BETWEEN 0 AND 28)
    AND (active_week_count IS NULL OR active_week_count BETWEEN 0 AND 4)
    AND (sales_day_ratio IS NULL OR sales_day_ratio BETWEEN 0 AND 1)
    AND (max_daily_sales IS NULL OR max_daily_sales >= 0)
    AND (median_daily_sales IS NULL OR median_daily_sales >= 0)
    AND (mad_daily_sales IS NULL OR mad_daily_sales >= 0)
    AND (max_transaction_quantity IS NULL OR max_transaction_quantity >= 0)
);
ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_signal CHECK (
    primary_demand_signal_type IS NULL
    OR primary_demand_signal_type IN (
        'DATA_INSUFFICIENT', 'KNOWN_EVENT', 'UNEXPLAINED_SPIKE',
        'INTERMITTENT', 'STABLE_REPEAT', 'VARIABLE'
    )
);
ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_confidence CHECK (
    demand_confidence IS NULL
    OR demand_confidence IN ('HIGH', 'MEDIUM', 'LOW', 'NONE')
);
ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_rates CHECK (
    (low_demand_rate IS NULL OR low_demand_rate >= 0)
    AND (base_demand_rate IS NULL OR base_demand_rate >= 0)
    AND (high_demand_rate IS NULL OR high_demand_rate >= 0)
    AND (
        low_demand_rate IS NULL
        OR base_demand_rate IS NULL
        OR high_demand_rate IS NULL
        OR (low_demand_rate <= base_demand_rate AND base_demand_rate <= high_demand_rate)
    )
);
ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_exception CHECK (
    inventory_exception_type IS NULL
    OR inventory_exception_type IN (
        'STOCKOUT_RISK', 'OVERSTOCK', 'REVIEW_REQUIRED', 'NORMAL', 'NON_ACTIONABLE'
    )
);
ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_severity CHECK (
    severity IS NULL OR severity IN ('CRITICAL', 'HIGH', 'REVIEW')
);
ALTER TABLE sp_inventory_metric ADD CONSTRAINT ck_sp_metric_mvp2_qty CHECK (
    expected_shortage_quantity IS NULL OR expected_shortage_quantity >= 0
);

CREATE TABLE sp_demand_event (
    demand_event_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    event_code VARCHAR2(64 CHAR) NOT NULL,
    event_type VARCHAR2(20 CHAR) NOT NULL,
    store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    uplift_low NUMBER(9, 6),
    uplift_base NUMBER(9, 6),
    uplift_high NUMBER(9, 6),
    input_snapshot_version VARCHAR2(64 CHAR) NOT NULL,
    source_type VARCHAR2(20 CHAR) DEFAULT 'SYNTHETIC' NOT NULL,
    assumption_type VARCHAR2(20 CHAR) DEFAULT 'ASSUMPTION' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_demand_event PRIMARY KEY (demand_event_id),
    CONSTRAINT fk_sp_event_store FOREIGN KEY (store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_event_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_event UNIQUE (event_code, store_id, sku_id, input_snapshot_version),
    CONSTRAINT ck_sp_event_type CHECK (
        event_type IN ('PROMOTION', 'PRICE_CHANGE', 'STORE_EVENT', 'OTHER')
    ),
    CONSTRAINT ck_sp_event_dates CHECK (
        start_date = TRUNC(start_date)
        AND end_date = TRUNC(end_date)
        AND start_date <= end_date
    ),
    CONSTRAINT ck_sp_event_uplift CHECK (
        (uplift_low IS NULL OR uplift_low > 0)
        AND (uplift_base IS NULL OR uplift_base > 0)
        AND (uplift_high IS NULL OR uplift_high > 0)
        AND (uplift_low IS NULL OR uplift_base IS NULL OR uplift_low <= uplift_base)
        AND (uplift_base IS NULL OR uplift_high IS NULL OR uplift_base <= uplift_high)
    ),
    CONSTRAINT ck_sp_event_source CHECK (source_type = 'SYNTHETIC'),
    CONSTRAINT ck_sp_event_assumption CHECK (assumption_type = 'ASSUMPTION')
);

CREATE TABLE sp_inbound_schedule (
    inbound_schedule_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    inbound_reference VARCHAR2(64 CHAR) NOT NULL,
    store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    quantity NUMBER(10, 0),
    eta_at TIMESTAMP(6) WITH TIME ZONE,
    inbound_status VARCHAR2(20 CHAR) NOT NULL,
    input_snapshot_version VARCHAR2(64 CHAR) NOT NULL,
    source_type VARCHAR2(20 CHAR) DEFAULT 'SYNTHETIC' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_inbound PRIMARY KEY (inbound_schedule_id),
    CONSTRAINT fk_sp_inbound_store FOREIGN KEY (store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_inbound_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_inbound UNIQUE (inbound_reference, input_snapshot_version),
    CONSTRAINT ck_sp_inbound_qty CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT ck_sp_inbound_status CHECK (
        inbound_status IN ('PLANNED', 'CONFIRMED', 'CANCELLED', 'RECEIVED')
    ),
    CONSTRAINT ck_sp_inbound_source CHECK (source_type = 'SYNTHETIC')
);

CREATE TABLE sp_open_transfer (
    open_transfer_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    transfer_reference VARCHAR2(64 CHAR) NOT NULL,
    donor_store_id VARCHAR2(64 CHAR) NOT NULL,
    receiver_store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    quantity NUMBER(10, 0) NOT NULL,
    eta_at TIMESTAMP(6) WITH TIME ZONE,
    transfer_status VARCHAR2(20 CHAR) NOT NULL,
    input_snapshot_version VARCHAR2(64 CHAR) NOT NULL,
    source_type VARCHAR2(20 CHAR) DEFAULT 'SYNTHETIC' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_open_transfer PRIMARY KEY (open_transfer_id),
    CONSTRAINT fk_sp_open_donor FOREIGN KEY (donor_store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_open_receiver FOREIGN KEY (receiver_store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_open_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_open_transfer UNIQUE (transfer_reference, input_snapshot_version),
    CONSTRAINT ck_sp_open_distinct CHECK (donor_store_id <> receiver_store_id),
    CONSTRAINT ck_sp_open_qty CHECK (quantity > 0),
    CONSTRAINT ck_sp_open_status CHECK (
        transfer_status IN ('REQUESTED', 'APPROVED', 'IN_TRANSIT', 'CANCELLED', 'RECEIVED')
    ),
    CONSTRAINT ck_sp_open_source CHECK (source_type = 'SYNTHETIC')
);

CREATE TABLE sp_store_transfer_route (
    route_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    donor_store_id VARCHAR2(64 CHAR) NOT NULL,
    receiver_store_id VARCHAR2(64 CHAR) NOT NULL,
    active_flag CHAR(1 CHAR) NOT NULL,
    owner_override_flag CHAR(1 CHAR) NOT NULL,
    lead_time_days NUMBER(5, 0) NOT NULL,
    minimum_quantity NUMBER(10, 0) NOT NULL,
    package_multiple NUMBER(10, 0) NOT NULL,
    maximum_quantity NUMBER(10, 0) NOT NULL,
    input_snapshot_version VARCHAR2(64 CHAR) NOT NULL,
    assumption_type VARCHAR2(20 CHAR) DEFAULT 'ASSUMPTION' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_transfer_route PRIMARY KEY (route_id),
    CONSTRAINT fk_sp_route_donor FOREIGN KEY (donor_store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_route_receiver FOREIGN KEY (receiver_store_id) REFERENCES sp_store (store_id),
    CONSTRAINT uq_sp_transfer_route UNIQUE (
        donor_store_id, receiver_store_id, input_snapshot_version
    ),
    CONSTRAINT ck_sp_route_distinct CHECK (donor_store_id <> receiver_store_id),
    CONSTRAINT ck_sp_route_flags CHECK (
        active_flag IN ('Y', 'N') AND owner_override_flag IN ('Y', 'N')
    ),
    CONSTRAINT ck_sp_route_values CHECK (
        lead_time_days >= 0
        AND minimum_quantity > 0
        AND package_multiple > 0
        AND maximum_quantity >= minimum_quantity
    ),
    CONSTRAINT ck_sp_route_assumption CHECK (assumption_type = 'ASSUMPTION')
);

CREATE TABLE sp_store_sku_policy (
    store_sku_policy_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    display_minimum NUMBER(10, 0) NOT NULL,
    safety_stock NUMBER(10, 0) NOT NULL,
    maximum_capacity NUMBER(10, 0) NOT NULL,
    target_coverage_days NUMBER(5, 0) NOT NULL,
    retained_days NUMBER(5, 0) NOT NULL,
    input_snapshot_version VARCHAR2(64 CHAR) NOT NULL,
    assumption_type VARCHAR2(20 CHAR) DEFAULT 'ASSUMPTION' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_store_sku_policy PRIMARY KEY (store_sku_policy_id),
    CONSTRAINT fk_sp_policy_store FOREIGN KEY (store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_policy_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_store_sku_policy UNIQUE (store_id, sku_id, input_snapshot_version),
    CONSTRAINT ck_sp_policy_values CHECK (
        display_minimum >= 0
        AND safety_stock >= 0
        AND maximum_capacity > 0
        AND display_minimum + safety_stock <= maximum_capacity
        AND target_coverage_days >= 0
        AND retained_days >= 0
    ),
    CONSTRAINT ck_sp_policy_assumption CHECK (assumption_type = 'ASSUMPTION')
);

CREATE TABLE sp_metric_quality_flag (
    metric_quality_flag_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    inventory_metric_id NUMBER(19, 0) NOT NULL,
    flag_code VARCHAR2(30 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_metric_quality PRIMARY KEY (metric_quality_flag_id),
    CONSTRAINT fk_sp_quality_metric FOREIGN KEY (inventory_metric_id)
        REFERENCES sp_inventory_metric (inventory_metric_id),
    CONSTRAINT uq_sp_metric_quality UNIQUE (inventory_metric_id, flag_code),
    CONSTRAINT ck_sp_quality_flag CHECK (
        flag_code IN (
            'OOS_CENSORED', 'STALE_INVENTORY',
            'MISSING_INBOUND', 'INCOMPLETE_EVENT_DATA'
        )
    )
);

ALTER TABLE sp_rebalance_recommendation ADD (
    route_id NUMBER(19, 0),
    candidate_status VARCHAR2(20 CHAR) DEFAULT 'ELIGIBLE' NOT NULL,
    candidate_version NUMBER(10, 0) DEFAULT 1 NOT NULL,
    recommendation_mode VARCHAR2(20 CHAR) DEFAULT 'RECOMMENDED' NOT NULL,
    projected_receiver_at_arrival NUMBER(12, 0),
    projected_donor_at_dispatch NUMBER(12, 0),
    receiver_capacity_remaining NUMBER(12, 0),
    evaluated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

ALTER TABLE sp_rebalance_recommendation MODIFY (
    receiver_shortage_quantity NULL,
    donor_transferable_quantity NULL,
    recommended_quantity NULL
);

ALTER TABLE sp_rebalance_recommendation DROP CONSTRAINT ck_sp_rec_qty;
ALTER TABLE sp_rebalance_recommendation ADD CONSTRAINT fk_sp_rec_route
    FOREIGN KEY (route_id) REFERENCES sp_store_transfer_route (route_id);
ALTER TABLE sp_rebalance_recommendation ADD CONSTRAINT ck_sp_rec_candidate CHECK (
    candidate_status IN ('ELIGIBLE', 'REJECTED')
    AND recommendation_mode IN ('RECOMMENDED', 'COMPARISON_ONLY', 'NONE')
    AND candidate_version > 0
    AND (receiver_capacity_remaining IS NULL OR receiver_capacity_remaining >= 0)
    AND (projected_donor_at_dispatch IS NULL OR projected_donor_at_dispatch >= 0)
    AND (
        (
            candidate_status = 'ELIGIBLE'
            AND receiver_shortage_quantity IS NOT NULL
            AND receiver_shortage_quantity > 0
            AND donor_transferable_quantity IS NOT NULL
            AND donor_transferable_quantity > 0
            AND (
                (
                    recommendation_mode = 'RECOMMENDED'
                    AND recommended_quantity IS NOT NULL
                    AND recommended_quantity > 0
                    AND recommended_quantity <= receiver_shortage_quantity
                    AND recommended_quantity <= donor_transferable_quantity
                )
                OR (
                    recommendation_mode = 'COMPARISON_ONLY'
                    AND recommended_quantity IS NULL
                )
            )
        )
        OR (
            candidate_status = 'REJECTED'
            AND recommendation_mode = 'NONE'
            AND recommended_quantity IS NULL
            AND (receiver_shortage_quantity IS NULL OR receiver_shortage_quantity >= 0)
            AND (donor_transferable_quantity IS NULL OR donor_transferable_quantity >= 0)
        )
    )
);

CREATE TABLE sp_candidate_reason (
    candidate_reason_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    recommendation_id NUMBER(19, 0) NOT NULL,
    reason_code VARCHAR2(40 CHAR) NOT NULL,
    reason_order NUMBER(5, 0) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_candidate_reason PRIMARY KEY (candidate_reason_id),
    CONSTRAINT fk_sp_reason_rec FOREIGN KEY (recommendation_id)
        REFERENCES sp_rebalance_recommendation (recommendation_id),
    CONSTRAINT uq_sp_candidate_reason UNIQUE (recommendation_id, reason_code),
    CONSTRAINT ck_sp_candidate_reason CHECK (
        reason_order > 0
        AND reason_code IN (
            'OWNER_MISMATCH', 'ROUTE_NOT_ALLOWED', 'LEAD_TIME_TOO_LONG',
            'INBOUND_ALREADY_COVERS', 'NO_TRANSFERABLE_STOCK',
            'DISPLAY_MINIMUM_VIOLATION', 'CAPACITY_EXCEEDED',
            'PENDING_TRANSFER_CONFLICT'
        )
    )
);

CREATE TABLE sp_rebalance_scenario (
    scenario_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    recommendation_id NUMBER(19, 0) NOT NULL,
    scenario_type VARCHAR2(20 CHAR) NOT NULL,
    demand_rate NUMBER(18, 12) NOT NULL,
    scenario_quantity NUMBER(10, 0) NOT NULL,
    package_multiple NUMBER(10, 0) NOT NULL,
    receiver_before_available NUMBER(12, 0) NOT NULL,
    receiver_after_available NUMBER(12, 0) NOT NULL,
    receiver_before_coverage NUMBER(18, 6),
    receiver_after_coverage NUMBER(18, 6),
    receiver_risk_code VARCHAR2(30 CHAR) NOT NULL,
    donor_before_available NUMBER(12, 0) NOT NULL,
    donor_after_available NUMBER(12, 0) NOT NULL,
    donor_before_coverage NUMBER(18, 6),
    donor_after_coverage NUMBER(18, 6),
    donor_risk_code VARCHAR2(30 CHAR) NOT NULL,
    lead_time_days NUMBER(5, 0) NOT NULL,
    expected_arrival_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    inbound_included_flag CHAR(1 CHAR) NOT NULL,
    warning_summary VARCHAR2(1000 CHAR),
    candidate_version NUMBER(10, 0) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_rebalance_scenario PRIMARY KEY (scenario_id),
    CONSTRAINT fk_sp_scenario_rec FOREIGN KEY (recommendation_id)
        REFERENCES sp_rebalance_recommendation (recommendation_id),
    CONSTRAINT uq_sp_scenario UNIQUE (recommendation_id, scenario_type),
    CONSTRAINT ck_sp_scenario_type CHECK (
        scenario_type IN ('NO_ACTION', 'CONSERVATIVE', 'BASE', 'AGGRESSIVE')
    ),
    CONSTRAINT ck_sp_scenario_values CHECK (
        demand_rate >= 0
        AND scenario_quantity >= 0
        AND package_multiple > 0
        AND (receiver_before_coverage IS NULL OR receiver_before_coverage >= 0)
        AND (receiver_after_coverage IS NULL OR receiver_after_coverage >= 0)
        AND (donor_before_coverage IS NULL OR donor_before_coverage >= 0)
        AND (donor_after_coverage IS NULL OR donor_after_coverage >= 0)
        AND lead_time_days >= 0
        AND inbound_included_flag IN ('Y', 'N')
        AND candidate_version > 0
        AND (
            (scenario_type = 'NO_ACTION' AND scenario_quantity = 0)
            OR scenario_type <> 'NO_ACTION'
        )
    ),
    CONSTRAINT ck_sp_scenario_receiver CHECK (
        receiver_risk_code IN (
            'STOCKOUT_RISK', 'OVERSTOCK', 'REVIEW_REQUIRED', 'NORMAL', 'NON_ACTIONABLE'
        )
    ),
    CONSTRAINT ck_sp_scenario_donor CHECK (
        donor_risk_code IN (
            'STOCKOUT_RISK', 'OVERSTOCK', 'REVIEW_REQUIRED', 'NORMAL', 'NON_ACTIONABLE'
        )
    )
);

ALTER TABLE sp_rebalance_decision ADD (
    decision_sequence NUMBER(10, 0) DEFAULT 1 NOT NULL,
    decision_contract_version VARCHAR2(32 CHAR) DEFAULT 'MVP-1' NOT NULL,
    reason_code VARCHAR2(40 CHAR),
    recommendation_version NUMBER(10, 0) DEFAULT 1 NOT NULL
);

ALTER TABLE sp_rebalance_decision MODIFY (
    selected_quantity NULL,
    reason NULL
);

ALTER TABLE sp_rebalance_decision DROP CONSTRAINT uq_sp_dec_rec;
ALTER TABLE sp_rebalance_decision DROP CONSTRAINT ck_sp_dec_status;
ALTER TABLE sp_rebalance_decision DROP CONSTRAINT ck_sp_dec_qty;
ALTER TABLE sp_rebalance_decision DROP CONSTRAINT ck_sp_dec_reason;

ALTER TABLE sp_rebalance_decision ADD CONSTRAINT uq_sp_dec_rec_seq
    UNIQUE (recommendation_id, decision_sequence);
ALTER TABLE sp_rebalance_decision ADD CONSTRAINT ck_sp_dec_sequence CHECK (
    decision_sequence > 0 AND recommendation_version > 0
);
ALTER TABLE sp_rebalance_decision ADD CONSTRAINT ck_sp_dec_contract CHECK (
    decision_contract_version IN ('MVP-1', 'MVP-2')
);
ALTER TABLE sp_rebalance_decision ADD CONSTRAINT ck_sp_dec_mvp2_shape CHECK (
    (
        decision_contract_version = 'MVP-1'
        AND decision_status IN ('APPROVED', 'REJECTED')
        AND selected_quantity IS NOT NULL
        AND selected_quantity > 0
        AND reason IS NOT NULL
        AND LENGTH(TRIM(reason)) > 0
    )
    OR (
        decision_contract_version = 'MVP-2'
        AND decision_status IN ('PENDING', 'HELD', 'APPROVED', 'REJECTED', 'EXPIRED')
        AND (
            (decision_status = 'PENDING' AND selected_quantity IS NULL)
            OR (
                decision_status = 'APPROVED'
                AND selected_quantity IS NOT NULL
                AND selected_quantity > 0
            )
            OR (
                decision_status IN ('HELD', 'REJECTED', 'EXPIRED')
                AND selected_quantity IS NULL
                AND reason_code IS NOT NULL
                AND LENGTH(TRIM(reason_code)) > 0
                AND reason IS NOT NULL
                AND LENGTH(TRIM(reason)) > 0
            )
        )
    )
);

CREATE TABLE sp_transfer_draft (
    transfer_draft_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    decision_id NUMBER(19, 0) NOT NULL,
    donor_store_id VARCHAR2(64 CHAR) NOT NULL,
    receiver_store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    quantity NUMBER(10, 0) NOT NULL,
    draft_status VARCHAR2(20 CHAR) DEFAULT 'CREATED' NOT NULL,
    external_reference VARCHAR2(100 CHAR),
    payload_version VARCHAR2(32 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_transfer_draft PRIMARY KEY (transfer_draft_id),
    CONSTRAINT fk_sp_draft_decision FOREIGN KEY (decision_id)
        REFERENCES sp_rebalance_decision (decision_id),
    CONSTRAINT fk_sp_draft_donor FOREIGN KEY (donor_store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_draft_receiver FOREIGN KEY (receiver_store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_draft_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_draft_decision UNIQUE (decision_id),
    CONSTRAINT ck_sp_draft_distinct CHECK (donor_store_id <> receiver_store_id),
    CONSTRAINT ck_sp_draft_qty CHECK (quantity > 0),
    CONSTRAINT ck_sp_draft_status CHECK (
        draft_status IN ('CREATED', 'READY', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT ck_sp_draft_payload CHECK (LENGTH(TRIM(payload_version)) > 0)
);

CREATE INDEX ix_sp_inv_mvp2_lookup ON sp_inventory_snapshot (
    sku_id, store_id, snapshot_date, input_snapshot_version
);
CREATE INDEX ix_sp_sale_mvp2_lookup ON sp_daily_sale (
    sku_id, store_id, sales_date, input_snapshot_version
);
CREATE INDEX ix_sp_metric_mvp2_queue ON sp_inventory_metric (
    analysis_run_id, severity, inventory_exception_type, demand_confidence
);
CREATE INDEX ix_sp_rec_candidate ON sp_rebalance_recommendation (
    receiver_metric_id, candidate_status, recommendation_mode
);
CREATE INDEX ix_sp_inbound_lookup ON sp_inbound_schedule (
    store_id, sku_id, eta_at, inbound_status, input_snapshot_version
);
CREATE INDEX ix_sp_open_lookup ON sp_open_transfer (
    sku_id, donor_store_id, receiver_store_id, transfer_status, input_snapshot_version
);

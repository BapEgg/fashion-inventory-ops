CREATE TABLE sp_product (
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    product_name VARCHAR2(200 CHAR) NOT NULL,
    category VARCHAR2(50 CHAR) NOT NULL,
    color VARCHAR2(50 CHAR) NOT NULL,
    size_name VARCHAR2(30 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_product PRIMARY KEY (sku_id)
);

CREATE TABLE sp_store (
    store_id VARCHAR2(64 CHAR) NOT NULL,
    store_name VARCHAR2(120 CHAR) NOT NULL,
    region VARCHAR2(80 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_store PRIMARY KEY (store_id)
);

CREATE TABLE sp_inventory_snapshot (
    inventory_snapshot_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    snapshot_date DATE NOT NULL,
    store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    on_hand_quantity NUMBER(10, 0) NOT NULL,
    reserved_quantity NUMBER(10, 0) NOT NULL,
    source_type VARCHAR2(20 CHAR) DEFAULT 'SYNTHETIC' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_inventory_snapshot PRIMARY KEY (inventory_snapshot_id),
    CONSTRAINT fk_sp_inv_store FOREIGN KEY (store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_inv_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_inv_snapshot UNIQUE (snapshot_date, store_id, sku_id),
    CONSTRAINT ck_sp_inv_date CHECK (snapshot_date = TRUNC(snapshot_date)),
    CONSTRAINT ck_sp_inv_qty CHECK (
        on_hand_quantity >= 0
        AND reserved_quantity >= 0
        AND reserved_quantity <= on_hand_quantity
    ),
    CONSTRAINT ck_sp_inv_source CHECK (source_type IN ('SYNTHETIC'))
);

CREATE TABLE sp_daily_sale (
    daily_sale_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    sales_date DATE NOT NULL,
    store_id VARCHAR2(64 CHAR) NOT NULL,
    sku_id VARCHAR2(64 CHAR) NOT NULL,
    sold_quantity NUMBER(10, 0) NOT NULL,
    source_type VARCHAR2(20 CHAR) DEFAULT 'SYNTHETIC' NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_daily_sale PRIMARY KEY (daily_sale_id),
    CONSTRAINT fk_sp_sale_store FOREIGN KEY (store_id) REFERENCES sp_store (store_id),
    CONSTRAINT fk_sp_sale_product FOREIGN KEY (sku_id) REFERENCES sp_product (sku_id),
    CONSTRAINT uq_sp_daily_sale UNIQUE (sales_date, store_id, sku_id),
    CONSTRAINT ck_sp_sale_date CHECK (sales_date = TRUNC(sales_date)),
    CONSTRAINT ck_sp_sale_qty CHECK (sold_quantity >= 0),
    CONSTRAINT ck_sp_sale_source CHECK (source_type IN ('SYNTHETIC'))
);

CREATE TABLE sp_analysis_run (
    analysis_run_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    analysis_date DATE NOT NULL,
    rule_version VARCHAR2(32 CHAR) NOT NULL,
    run_status VARCHAR2(20 CHAR) DEFAULT 'RUNNING' NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_sp_analysis_run PRIMARY KEY (analysis_run_id),
    CONSTRAINT uq_sp_analysis_run UNIQUE (analysis_date, rule_version),
    CONSTRAINT ck_sp_analysis_date CHECK (analysis_date = TRUNC(analysis_date)),
    CONSTRAINT ck_sp_run_status CHECK (run_status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE sp_inventory_metric (
    inventory_metric_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    analysis_run_id NUMBER(19, 0) NOT NULL,
    inventory_snapshot_id NUMBER(19, 0) NOT NULL,
    available_quantity NUMBER(10, 0) NOT NULL,
    average_daily_sales NUMBER(12, 4) NOT NULL,
    coverage_days NUMBER(12, 2),
    classification VARCHAR2(30 CHAR) NOT NULL,
    priority VARCHAR2(20 CHAR),
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_inventory_metric PRIMARY KEY (inventory_metric_id),
    CONSTRAINT fk_sp_metric_run FOREIGN KEY (analysis_run_id) REFERENCES sp_analysis_run (analysis_run_id),
    CONSTRAINT fk_sp_metric_snapshot FOREIGN KEY (inventory_snapshot_id) REFERENCES sp_inventory_snapshot (inventory_snapshot_id),
    CONSTRAINT uq_sp_metric UNIQUE (analysis_run_id, inventory_snapshot_id),
    CONSTRAINT ck_sp_metric_values CHECK (
        available_quantity >= 0
        AND average_daily_sales >= 0
        AND (coverage_days IS NULL OR coverage_days >= 0)
    ),
    CONSTRAINT ck_sp_classification CHECK (
        classification IN ('STOCKOUT_RISK', 'OVERSTOCK', 'NORMAL', 'NON_ACTIONABLE')
    ),
    CONSTRAINT ck_sp_priority CHECK (priority IS NULL OR priority IN ('CRITICAL', 'HIGH'))
);

CREATE TABLE sp_rebalance_recommendation (
    recommendation_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    receiver_metric_id NUMBER(19, 0) NOT NULL,
    donor_metric_id NUMBER(19, 0) NOT NULL,
    receiver_shortage_quantity NUMBER(10, 0) NOT NULL,
    donor_transferable_quantity NUMBER(10, 0) NOT NULL,
    recommended_quantity NUMBER(10, 0) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_rebalance_rec PRIMARY KEY (recommendation_id),
    CONSTRAINT fk_sp_rec_receiver FOREIGN KEY (receiver_metric_id) REFERENCES sp_inventory_metric (inventory_metric_id),
    CONSTRAINT fk_sp_rec_donor FOREIGN KEY (donor_metric_id) REFERENCES sp_inventory_metric (inventory_metric_id),
    CONSTRAINT uq_sp_rec_pair UNIQUE (receiver_metric_id, donor_metric_id),
    CONSTRAINT ck_sp_rec_distinct CHECK (receiver_metric_id <> donor_metric_id),
    CONSTRAINT ck_sp_rec_qty CHECK (
        receiver_shortage_quantity > 0
        AND donor_transferable_quantity > 0
        AND recommended_quantity > 0
        AND recommended_quantity <= receiver_shortage_quantity
        AND recommended_quantity <= donor_transferable_quantity
    )
);

CREATE TABLE sp_rebalance_decision (
    decision_id NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    recommendation_id NUMBER(19, 0) NOT NULL,
    decision_status VARCHAR2(20 CHAR) NOT NULL,
    selected_quantity NUMBER(10, 0) NOT NULL,
    reason VARCHAR2(1000 CHAR) NOT NULL,
    actor_label VARCHAR2(100 CHAR) NOT NULL,
    decided_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_sp_rebalance_dec PRIMARY KEY (decision_id),
    CONSTRAINT fk_sp_dec_rec FOREIGN KEY (recommendation_id) REFERENCES sp_rebalance_recommendation (recommendation_id),
    CONSTRAINT uq_sp_dec_rec UNIQUE (recommendation_id),
    CONSTRAINT ck_sp_dec_status CHECK (decision_status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_sp_dec_qty CHECK (selected_quantity > 0),
    CONSTRAINT ck_sp_dec_reason CHECK (LENGTH(TRIM(reason)) > 0),
    CONSTRAINT ck_sp_dec_actor CHECK (LENGTH(TRIM(actor_label)) > 0)
);

CREATE INDEX ix_sp_inv_lookup ON sp_inventory_snapshot (sku_id, snapshot_date, store_id);
CREATE INDEX ix_sp_sale_lookup ON sp_daily_sale (sku_id, store_id, sales_date);
CREATE INDEX ix_sp_metric_exception ON sp_inventory_metric (analysis_run_id, classification, priority);
CREATE INDEX ix_sp_rec_receiver ON sp_rebalance_recommendation (receiver_metric_id);

COMMENT ON TABLE sp_inventory_snapshot IS 'SYNTHETIC store-SKU inventory evidence captured for a date';
COMMENT ON TABLE sp_daily_sale IS 'SYNTHETIC daily store-SKU sales evidence';
COMMENT ON TABLE sp_analysis_run IS 'Idempotent Batch analysis boundary identified by date and rule version';
COMMENT ON TABLE sp_inventory_metric IS 'Deterministic Java-calculated inventory analysis result';
COMMENT ON TABLE sp_rebalance_recommendation IS 'Deterministic inter-store transfer recommendation';
COMMENT ON TABLE sp_rebalance_decision IS 'Terminal user approval or rejection audit record';

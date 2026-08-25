-- StockPilot MVP-2 synthetic input scenarios GS-01 through GS-06.
-- These rows are versioned SYNTHETIC data and ASSUMPTION inputs for a demo.
-- They are not actual F&F policy, operational data, or a validated industry standard.

INSERT ALL
    INTO sp_product (
        sku_id, product_name, category, color, size_name,
        launch_date, season_code, sales_status
    ) VALUES (
        'SKU-MVP2-GS01-STABLE', 'GS-01 안정 반복 수요', 'APPAREL', 'BLACK', 'M',
        DATE '2026-01-01', 'DEMO-2026', 'ACTIVE'
    )
    INTO sp_product (
        sku_id, product_name, category, color, size_name,
        launch_date, season_code, sales_status
    ) VALUES (
        'SKU-MVP2-GS02-EVENT', 'GS-02 등록 이벤트 수요', 'APPAREL', 'WHITE', 'M',
        DATE '2026-01-01', 'DEMO-2026', 'ACTIVE'
    )
    INTO sp_product (
        sku_id, product_name, category, color, size_name,
        launch_date, season_code, sales_status
    ) VALUES (
        'SKU-MVP2-GS03-SPIKE', 'GS-03 단일 거래 급증', 'APPAREL', 'BLUE', 'M',
        DATE '2026-01-01', 'DEMO-2026', 'ACTIVE'
    )
    INTO sp_product (
        sku_id, product_name, category, color, size_name,
        launch_date, season_code, sales_status
    ) VALUES (
        'SKU-MVP2-GS04-OOS', 'GS-04 품절 검열', 'APPAREL', 'GREEN', 'M',
        DATE '2026-01-01', 'DEMO-2026', 'ACTIVE'
    )
    INTO sp_product (
        sku_id, product_name, category, color, size_name,
        launch_date, season_code, sales_status
    ) VALUES (
        'SKU-MVP2-GS05-INBOUND', 'GS-05 확정 입고', 'APPAREL', 'BEIGE', 'M',
        DATE '2026-01-01', 'DEMO-2026', 'ACTIVE'
    )
    INTO sp_product (
        sku_id, product_name, category, color, size_name,
        launch_date, season_code, sales_status
    ) VALUES (
        'SKU-MVP2-GS06-ROUTE', 'GS-06 소유권 경로 제약', 'APPAREL', 'RED', 'M',
        DATE '2026-01-01', 'DEMO-2026', 'ACTIVE'
    )
SELECT 1 FROM dual;

INSERT ALL
    INTO sp_store (
        store_id, store_name, region, store_type,
        inventory_owner_code, transfer_zone
    ) VALUES (
        'STORE-MVP2-RECEIVER-A', 'MVP-2 수요 매장 A', 'SEOUL', 'DIRECT',
        'OWNER-DEMO-A', 'DOMESTIC'
    )
    INTO sp_store (
        store_id, store_name, region, store_type,
        inventory_owner_code, transfer_zone
    ) VALUES (
        'STORE-MVP2-DONOR-A', 'MVP-2 공급 매장 A', 'SEOUL', 'DIRECT',
        'OWNER-DEMO-A', 'DOMESTIC'
    )
    INTO sp_store (
        store_id, store_name, region, store_type,
        inventory_owner_code, transfer_zone
    ) VALUES (
        'STORE-MVP2-DONOR-B', 'MVP-2 공급 매장 B', 'BUSAN', 'DIRECT',
        'OWNER-DEMO-B', 'DOMESTIC'
    )
SELECT 1 FROM dual;

INSERT INTO sp_inventory_snapshot (
    snapshot_date,
    snapshot_at,
    store_id,
    sku_id,
    on_hand_quantity,
    reserved_quantity,
    out_of_stock_flag,
    input_snapshot_version,
    source_type
)
WITH scenario_products (
    sku_id, donor_store_id, receiver_current, donor_current
) AS (
    SELECT 'SKU-MVP2-GS01-STABLE', 'STORE-MVP2-DONOR-A', 4, 80 FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS02-EVENT', 'STORE-MVP2-DONOR-A', 5, 80 FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS03-SPIKE', 'STORE-MVP2-DONOR-A', 4, 80 FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS04-OOS', 'STORE-MVP2-DONOR-A', 3, 80 FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS05-INBOUND', 'STORE-MVP2-DONOR-A', 2, 80 FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS06-ROUTE', 'STORE-MVP2-DONOR-B', 2, 90 FROM dual
),
day_numbers (day_number) AS (
    SELECT LEVEL - 1 FROM dual CONNECT BY LEVEL <= 29
),
store_roles (
    sku_id, store_id, store_role, receiver_current, donor_current
) AS (
    SELECT
        sku_id, 'STORE-MVP2-RECEIVER-A', 'RECEIVER',
        receiver_current, donor_current
    FROM scenario_products
    UNION ALL
    SELECT
        sku_id, donor_store_id, 'DONOR',
        receiver_current, donor_current
    FROM scenario_products
)
SELECT
    DATE '2026-09-02' + day_number,
    FROM_TZ(
        CAST(DATE '2026-09-02' + day_number AS TIMESTAMP),
        'Asia/Seoul'
    ) + NUMTODSINTERVAL(8, 'HOUR'),
    store_id,
    sku_id,
    CASE
        WHEN day_number = 28 AND store_role = 'RECEIVER' THEN receiver_current
        WHEN day_number = 28 AND store_role = 'DONOR' THEN donor_current
        WHEN sku_id = 'SKU-MVP2-GS04-OOS'
            AND store_role = 'RECEIVER'
            AND day_number < 14 THEN 0
        WHEN store_role = 'RECEIVER' THEN 20
        ELSE 80
    END,
    0,
    CASE
        WHEN sku_id = 'SKU-MVP2-GS04-OOS'
            AND store_role = 'RECEIVER'
            AND day_number < 14 THEN 'Y'
        ELSE 'N'
    END,
    'MVP-2-GS-V1',
    'SYNTHETIC'
FROM store_roles
CROSS JOIN day_numbers;

INSERT INTO sp_daily_sale (
    sales_date,
    store_id,
    sku_id,
    sold_quantity,
    transaction_count,
    max_transaction_quantity,
    average_selling_price,
    input_snapshot_version,
    source_type
)
WITH scenario_products (sku_id, donor_store_id) AS (
    SELECT 'SKU-MVP2-GS01-STABLE', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS02-EVENT', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS03-SPIKE', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS04-OOS', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS05-INBOUND', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS06-ROUTE', 'STORE-MVP2-DONOR-B' FROM dual
),
day_numbers (day_number) AS (
    SELECT LEVEL - 1 FROM dual CONNECT BY LEVEL <= 28
),
store_roles (sku_id, store_id, store_role) AS (
    SELECT sku_id, 'STORE-MVP2-RECEIVER-A', 'RECEIVER'
    FROM scenario_products
    UNION ALL
    SELECT sku_id, donor_store_id, 'DONOR'
    FROM scenario_products
),
daily_quantities (
    sales_date, store_id, sku_id, store_role, sold_quantity
) AS (
    SELECT
        DATE '2026-09-02' + day_number,
        store_id,
        sku_id,
        store_role,
        CASE
            WHEN store_role = 'DONOR' THEN 1
            WHEN sku_id IN (
                'SKU-MVP2-GS01-STABLE',
                'SKU-MVP2-GS02-EVENT'
            ) THEN 2
            WHEN sku_id = 'SKU-MVP2-GS03-SPIKE'
                AND day_number = 18 THEN 20
            WHEN sku_id = 'SKU-MVP2-GS03-SPIKE' THEN 0
            WHEN sku_id = 'SKU-MVP2-GS04-OOS'
                AND day_number < 14 THEN 0
            WHEN sku_id = 'SKU-MVP2-GS04-OOS' THEN 2
            WHEN sku_id IN (
                'SKU-MVP2-GS05-INBOUND',
                'SKU-MVP2-GS06-ROUTE'
            ) THEN 3
        END
    FROM store_roles
    CROSS JOIN day_numbers
)
SELECT
    sales_date,
    store_id,
    sku_id,
    sold_quantity,
    CASE
        WHEN sold_quantity = 0 THEN 0
        WHEN sku_id = 'SKU-MVP2-GS03-SPIKE'
            AND store_role = 'RECEIVER' THEN 1
        ELSE sold_quantity
    END,
    CASE
        WHEN sold_quantity = 0 THEN 0
        WHEN sku_id = 'SKU-MVP2-GS03-SPIKE'
            AND store_role = 'RECEIVER' THEN sold_quantity
        ELSE 1
    END,
    100000.00,
    'MVP-2-GS-V1',
    'SYNTHETIC'
FROM daily_quantities;

INSERT INTO sp_demand_event (
    event_code,
    event_type,
    store_id,
    sku_id,
    start_date,
    end_date,
    uplift_low,
    uplift_base,
    uplift_high,
    input_snapshot_version,
    source_type,
    assumption_type
) VALUES (
    'EVENT-MVP2-GS02',
    'PROMOTION',
    'STORE-MVP2-RECEIVER-A',
    'SKU-MVP2-GS02-EVENT',
    DATE '2026-09-29',
    DATE '2026-10-07',
    1.20,
    1.50,
    1.80,
    'MVP-2-GS-V1',
    'SYNTHETIC',
    'ASSUMPTION'
);

INSERT INTO sp_inbound_schedule (
    inbound_reference,
    store_id,
    sku_id,
    quantity,
    eta_at,
    inbound_status,
    input_snapshot_version,
    source_type
) VALUES (
    'INBOUND-MVP2-GS05',
    'STORE-MVP2-RECEIVER-A',
    'SKU-MVP2-GS05-INBOUND',
    50,
    TO_TIMESTAMP_TZ(
        '2026-10-01 09:00:00 +09:00',
        'YYYY-MM-DD HH24:MI:SS TZH:TZM'
    ),
    'CONFIRMED',
    'MVP-2-GS-V1',
    'SYNTHETIC'
);

INSERT INTO sp_open_transfer (
    transfer_reference,
    donor_store_id,
    receiver_store_id,
    sku_id,
    quantity,
    eta_at,
    transfer_status,
    input_snapshot_version,
    source_type
) VALUES (
    'TRANSFER-MVP2-GS01',
    'STORE-MVP2-DONOR-A',
    'STORE-MVP2-RECEIVER-A',
    'SKU-MVP2-GS01-STABLE',
    2,
    TO_TIMESTAMP_TZ(
        '2026-10-01 09:00:00 +09:00',
        'YYYY-MM-DD HH24:MI:SS TZH:TZM'
    ),
    'APPROVED',
    'MVP-2-GS-V1',
    'SYNTHETIC'
);

INSERT INTO sp_store_transfer_route (
    donor_store_id,
    receiver_store_id,
    active_flag,
    owner_override_flag,
    lead_time_days,
    minimum_quantity,
    package_multiple,
    maximum_quantity,
    input_snapshot_version,
    assumption_type
) VALUES (
    'STORE-MVP2-DONOR-A',
    'STORE-MVP2-RECEIVER-A',
    'Y',
    'N',
    1,
    1,
    1,
    50,
    'MVP-2-GS-V1',
    'ASSUMPTION'
);

INSERT INTO sp_store_transfer_route (
    donor_store_id,
    receiver_store_id,
    active_flag,
    owner_override_flag,
    lead_time_days,
    minimum_quantity,
    package_multiple,
    maximum_quantity,
    input_snapshot_version,
    assumption_type
) VALUES (
    'STORE-MVP2-DONOR-B',
    'STORE-MVP2-RECEIVER-A',
    'Y',
    'N',
    10,
    1,
    1,
    50,
    'MVP-2-GS-V1',
    'ASSUMPTION'
);

INSERT INTO sp_store_sku_policy (
    store_id,
    sku_id,
    display_minimum,
    safety_stock,
    maximum_capacity,
    target_coverage_days,
    retained_days,
    input_snapshot_version,
    assumption_type
)
WITH scenario_products (sku_id, donor_store_id) AS (
    SELECT 'SKU-MVP2-GS01-STABLE', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS02-EVENT', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS03-SPIKE', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS04-OOS', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS05-INBOUND', 'STORE-MVP2-DONOR-A' FROM dual
    UNION ALL SELECT 'SKU-MVP2-GS06-ROUTE', 'STORE-MVP2-DONOR-B' FROM dual
),
store_skus (store_id, sku_id) AS (
    SELECT 'STORE-MVP2-RECEIVER-A', sku_id
    FROM scenario_products
    UNION ALL
    SELECT donor_store_id, sku_id
    FROM scenario_products
)
SELECT
    store_id,
    sku_id,
    1,
    2,
    100,
    7,
    14,
    'MVP-2-GS-V1',
    'ASSUMPTION'
FROM store_skus;

DECLARE
    product_count NUMBER;
    store_count NUMBER;
    inventory_count NUMBER;
    sales_count NUMBER;
    event_count NUMBER;
    inbound_count NUMBER;
    open_transfer_count NUMBER;
    route_count NUMBER;
    policy_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO product_count
    FROM sp_product
    WHERE sku_id LIKE 'SKU-MVP2-GS%';

    SELECT COUNT(*) INTO store_count
    FROM sp_store
    WHERE store_id LIKE 'STORE-MVP2-%';

    SELECT COUNT(*) INTO inventory_count
    FROM sp_inventory_snapshot
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    SELECT COUNT(*) INTO sales_count
    FROM sp_daily_sale
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    SELECT COUNT(*) INTO event_count
    FROM sp_demand_event
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    SELECT COUNT(*) INTO inbound_count
    FROM sp_inbound_schedule
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    SELECT COUNT(*) INTO open_transfer_count
    FROM sp_open_transfer
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    SELECT COUNT(*) INTO route_count
    FROM sp_store_transfer_route
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    SELECT COUNT(*) INTO policy_count
    FROM sp_store_sku_policy
    WHERE input_snapshot_version = 'MVP-2-GS-V1';

    IF product_count <> 6
        OR store_count <> 3
        OR inventory_count <> 348
        OR sales_count <> 336
        OR event_count <> 1
        OR inbound_count <> 1
        OR open_transfer_count <> 1
        OR route_count <> 2
        OR policy_count <> 12
    THEN
        RAISE_APPLICATION_ERROR(
            -20002,
            'MVP-2 synthetic scenario row counts do not match the approved contract.'
        );
    END IF;
END;
/

-- Adds a second, larger SYNTHETIC/DEMO scenario set under its own input_snapshot_version so the
-- exception list has enough rows to span multiple pages for demo-recording purposes. This is
-- fully additive and isolated from the V7 golden scenario: it reuses the existing product catalog
-- (sp_product has no version column) and existing stores, tags every version-scoped row with
-- 'FASHION-2026-FW-SEP-V2', and never touches 'FASHION-2026-FW-SEP-V1' rows -- so it cannot affect
-- the V7-scoped golden-scenario assertions (exactly 12 metrics / 11 recommended quantity).

INSERT ALL
    INTO sp_store (store_id, store_name, region, store_type, inventory_owner_code, transfer_zone)
    VALUES ('STORE-SEOUL-SINCHON', '신촌점', 'SEOUL', 'DIRECT', 'OWNER-DEMO-A', 'DOMESTIC')
    INTO sp_store (store_id, store_name, region, store_type, inventory_owner_code, transfer_zone)
    VALUES ('STORE-SEOUL-JAMSIL', '잠실점', 'SEOUL', 'DIRECT', 'OWNER-DEMO-A', 'DOMESTIC')
    INTO sp_store (store_id, store_name, region, store_type, inventory_owner_code, transfer_zone)
    VALUES ('STORE-DAEGU-CENTRAL', '대구점', 'DAEGU', 'DIRECT', 'OWNER-DEMO-A', 'DOMESTIC')
    INTO sp_store (store_id, store_name, region, store_type, inventory_owner_code, transfer_zone)
    VALUES ('STORE-GWANGJU-CENTRAL', '광주점', 'GWANGJU', 'DIRECT', 'OWNER-DEMO-A', 'DOMESTIC')
    INTO sp_store (store_id, store_name, region, store_type, inventory_owner_code, transfer_zone)
    VALUES ('STORE-INCHEON-CENTRAL', '인천점', 'INCHEON', 'DIRECT', 'OWNER-DEMO-A', 'DOMESTIC')
    INTO sp_store (store_id, store_name, region, store_type, inventory_owner_code, transfer_zone)
    VALUES ('STORE-SUWON-CENTRAL', '수원점', 'SUWON', 'DIRECT', 'OWNER-DEMO-A', 'DOMESTIC')
SELECT 1 FROM dual;

INSERT INTO sp_inventory_snapshot (
    snapshot_date, snapshot_at, store_id, sku_id,
    on_hand_quantity, reserved_quantity, out_of_stock_flag,
    input_snapshot_version, source_type
)
WITH expansion_stores (store_id, pattern) AS (
    SELECT 'STORE-SEOUL-GANGNAM-FLAGSHIP', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-HONGDAE', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-BUSAN-CENTUM', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-SINCHON', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-JAMSIL', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-DAEGU-CENTRAL', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-GWANGJU-CENTRAL', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-INCHEON-CENTRAL', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-SUWON-CENTRAL', 'OVERSTOCK' FROM dual
),
expansion_skus (sku_id) AS (
    SELECT 'FW26-TS-001-BK-M' FROM dual
    UNION ALL SELECT 'FW26-SH-014-WH-M' FROM dual
    UNION ALL SELECT 'FW26-DJ-007-BL-M' FROM dual
    UNION ALL SELECT 'FW26-KN-021-GN-M' FROM dual
    UNION ALL SELECT 'FW26-TC-005-BE-M' FROM dual
    UNION ALL SELECT 'FW26-OP-018-RD-M' FROM dual
),
day_numbers (day_number) AS (
    SELECT LEVEL - 1 FROM dual CONNECT BY LEVEL <= 29
)
SELECT
    DATE '2026-09-02' + day_number,
    FROM_TZ(CAST(DATE '2026-09-02' + day_number AS TIMESTAMP), 'Asia/Seoul')
        + NUMTODSINTERVAL(8, 'HOUR'),
    s.store_id,
    k.sku_id,
    CASE WHEN s.pattern = 'STOCKOUT' THEN 3 ELSE 65 END,
    0,
    'N',
    'FASHION-2026-FW-SEP-V2',
    'SYNTHETIC'
FROM expansion_stores s
CROSS JOIN expansion_skus k
CROSS JOIN day_numbers;

INSERT INTO sp_daily_sale (
    sales_date, store_id, sku_id, sold_quantity, transaction_count,
    max_transaction_quantity, average_selling_price,
    input_snapshot_version, source_type
)
WITH expansion_stores (store_id, pattern) AS (
    SELECT 'STORE-SEOUL-GANGNAM-FLAGSHIP', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-HONGDAE', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-BUSAN-CENTUM', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-SINCHON', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-JAMSIL', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-DAEGU-CENTRAL', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-GWANGJU-CENTRAL', 'OVERSTOCK' FROM dual
    UNION ALL SELECT 'STORE-INCHEON-CENTRAL', 'STOCKOUT' FROM dual
    UNION ALL SELECT 'STORE-SUWON-CENTRAL', 'OVERSTOCK' FROM dual
),
expansion_skus (sku_id) AS (
    SELECT 'FW26-TS-001-BK-M' FROM dual
    UNION ALL SELECT 'FW26-SH-014-WH-M' FROM dual
    UNION ALL SELECT 'FW26-DJ-007-BL-M' FROM dual
    UNION ALL SELECT 'FW26-KN-021-GN-M' FROM dual
    UNION ALL SELECT 'FW26-TC-005-BE-M' FROM dual
    UNION ALL SELECT 'FW26-OP-018-RD-M' FROM dual
),
day_numbers (day_number) AS (
    SELECT LEVEL - 1 FROM dual CONNECT BY LEVEL <= 28
)
SELECT
    DATE '2026-09-02' + day_number,
    s.store_id,
    k.sku_id,
    CASE WHEN s.pattern = 'STOCKOUT' THEN 2 ELSE 1 END,
    CASE WHEN s.pattern = 'STOCKOUT' THEN 2 ELSE 1 END,
    1,
    100000.00,
    'FASHION-2026-FW-SEP-V2',
    'SYNTHETIC'
FROM expansion_stores s
CROSS JOIN expansion_skus k
CROSS JOIN day_numbers;

INSERT INTO sp_store_sku_policy (
    store_id, sku_id, display_minimum, safety_stock, maximum_capacity,
    target_coverage_days, retained_days, input_snapshot_version, assumption_type
)
WITH expansion_stores (store_id) AS (
    SELECT 'STORE-SEOUL-GANGNAM-FLAGSHIP' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-HONGDAE' FROM dual
    UNION ALL SELECT 'STORE-BUSAN-CENTUM' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-SINCHON' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-JAMSIL' FROM dual
    UNION ALL SELECT 'STORE-DAEGU-CENTRAL' FROM dual
    UNION ALL SELECT 'STORE-GWANGJU-CENTRAL' FROM dual
    UNION ALL SELECT 'STORE-INCHEON-CENTRAL' FROM dual
    UNION ALL SELECT 'STORE-SUWON-CENTRAL' FROM dual
),
expansion_skus (sku_id) AS (
    SELECT 'FW26-TS-001-BK-M' FROM dual
    UNION ALL SELECT 'FW26-SH-014-WH-M' FROM dual
    UNION ALL SELECT 'FW26-DJ-007-BL-M' FROM dual
    UNION ALL SELECT 'FW26-KN-021-GN-M' FROM dual
    UNION ALL SELECT 'FW26-TC-005-BE-M' FROM dual
    UNION ALL SELECT 'FW26-OP-018-RD-M' FROM dual
)
SELECT
    s.store_id, k.sku_id, 1, 2, 100, 7, 14,
    'FASHION-2026-FW-SEP-V2', 'ASSUMPTION'
FROM expansion_stores s
CROSS JOIN expansion_skus k;

INSERT INTO sp_store_transfer_route (
    donor_store_id, receiver_store_id, active_flag, owner_override_flag,
    lead_time_days, minimum_quantity, package_multiple, maximum_quantity,
    input_snapshot_version, assumption_type
)
WITH donors (donor_store_id, lead_time_days) AS (
    SELECT 'STORE-SEOUL-HONGDAE', 1 FROM dual
    UNION ALL SELECT 'STORE-BUSAN-CENTUM', 3 FROM dual
    UNION ALL SELECT 'STORE-SEOUL-JAMSIL', 1 FROM dual
    UNION ALL SELECT 'STORE-GWANGJU-CENTRAL', 4 FROM dual
    UNION ALL SELECT 'STORE-SUWON-CENTRAL', 2 FROM dual
),
receivers (receiver_store_id) AS (
    SELECT 'STORE-SEOUL-GANGNAM-FLAGSHIP' FROM dual
    UNION ALL SELECT 'STORE-SEOUL-SINCHON' FROM dual
    UNION ALL SELECT 'STORE-DAEGU-CENTRAL' FROM dual
    UNION ALL SELECT 'STORE-INCHEON-CENTRAL' FROM dual
)
SELECT
    d.donor_store_id, r.receiver_store_id, 'Y', 'N',
    d.lead_time_days, 1, 1, 50,
    'FASHION-2026-FW-SEP-V2', 'ASSUMPTION'
FROM donors d
CROSS JOIN receivers r
WHERE d.donor_store_id <> r.receiver_store_id;

DECLARE
    store_count NUMBER;
    inventory_count NUMBER;
    sales_count NUMBER;
    policy_count NUMBER;
    route_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO store_count
    FROM sp_store
    WHERE store_id IN (
        'STORE-SEOUL-SINCHON', 'STORE-SEOUL-JAMSIL', 'STORE-DAEGU-CENTRAL',
        'STORE-GWANGJU-CENTRAL', 'STORE-INCHEON-CENTRAL', 'STORE-SUWON-CENTRAL'
    );

    SELECT COUNT(*) INTO inventory_count
    FROM sp_inventory_snapshot
    WHERE input_snapshot_version = 'FASHION-2026-FW-SEP-V2';

    SELECT COUNT(*) INTO sales_count
    FROM sp_daily_sale
    WHERE input_snapshot_version = 'FASHION-2026-FW-SEP-V2';

    SELECT COUNT(*) INTO policy_count
    FROM sp_store_sku_policy
    WHERE input_snapshot_version = 'FASHION-2026-FW-SEP-V2';

    SELECT COUNT(*) INTO route_count
    FROM sp_store_transfer_route
    WHERE input_snapshot_version = 'FASHION-2026-FW-SEP-V2';

    IF store_count <> 6
        OR inventory_count <> 1566
        OR sales_count <> 1512
        OR policy_count <> 54
        OR route_count <> 20
    THEN
        RAISE_APPLICATION_ERROR(
            -20003,
            'V2 demo scenario expansion row counts do not match the expected contract.'
        );
    END IF;
END;
/

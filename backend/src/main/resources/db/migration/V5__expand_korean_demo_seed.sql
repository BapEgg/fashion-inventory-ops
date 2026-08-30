-- Expanded SYNTHETIC demo data for a denser, Korean-language operations screen.
-- Existing technical IDs and the 2026-08-25 Golden Scenario facts remain unchanged.

UPDATE sp_product
SET product_name = '베이직 볼캡', category = '모자', color = '블랙', size_name = 'FREE'
WHERE sku_id = 'SKU-CAP-BLACK-FREE';

UPDATE sp_store SET store_name = '강남 플래그십', region = '서울' WHERE store_id = 'STORE-GANGNAM';
UPDATE sp_store SET store_name = '홍대점', region = '서울' WHERE store_id = 'STORE-HONGDAE';
UPDATE sp_store SET store_name = '성수점', region = '서울' WHERE store_id = 'STORE-SEONGSU';

INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-HOODIE-GRAY-M', '오버핏 후드 집업', '상의', '멜란지 그레이', 'M');
INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-DENIM-BLUE-M', '릴랙스 데님 팬츠', '하의', '미드 블루', 'M');
INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-CARDIGAN-IVORY-S', '크롭 니트 카디건', '상의', '아이보리', 'S');
INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-VEST-BLACK-M', '라이트 다운 베스트', '아우터', '블랙', 'M');
INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-SHIRT-WHITE-L', '클래식 옥스퍼드 셔츠', '상의', '화이트', 'L');
INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-SKIRT-NAVY-S', '플리츠 미디 스커트', '하의', '네이비', 'S');
INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-BAG-BROWN-FREE', '레더 미니 크로스백', '가방', '브라운', 'FREE');

INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-JAMSIL', '잠실 롯데월드몰점', '서울');
INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-YEOUIDO', '여의도 더현대서울점', '서울');
INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-BUSAN-SEOMYEON', '부산 서면점', '부산');
INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-DAEGU-DONGSEONGRO', '대구 동성로점', '대구');
INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-DAEJEON-SHINSEGAE', '대전 신세계점', '대전');

INSERT INTO sp_inventory_snapshot (
    snapshot_date, store_id, sku_id, on_hand_quantity, reserved_quantity, source_type
)
SELECT DATE '2026-08-26', stores.store_id, products.sku_id,
       CASE stores.store_group
           WHEN 'FAST' THEN 7 + products.product_no
           WHEN 'FAST_2' THEN 6 + products.product_no
           WHEN 'SLOW' THEN 48 + (products.product_no * 2)
           WHEN 'SLOW_2' THEN 55 + products.product_no
           WHEN 'SLOW_3' THEN 38 + products.product_no
           WHEN 'NORMAL_1' THEN 23 + products.product_no
           WHEN 'NORMAL_2' THEN 19 + products.product_no
           ELSE 17 + products.product_no
       END AS on_hand_quantity,
       1 + MOD(products.product_no + stores.store_no, 3) AS reserved_quantity,
       'SYNTHETIC'
FROM (
    SELECT 'STORE-GANGNAM' store_id, 1 store_no, 'FAST' store_group FROM dual UNION ALL
    SELECT 'STORE-HONGDAE', 2, 'SLOW' FROM dual UNION ALL
    SELECT 'STORE-SEONGSU', 3, 'NORMAL_1' FROM dual UNION ALL
    SELECT 'STORE-JAMSIL', 4, 'FAST_2' FROM dual UNION ALL
    SELECT 'STORE-YEOUIDO', 5, 'SLOW_2' FROM dual UNION ALL
    SELECT 'STORE-BUSAN-SEOMYEON', 6, 'NORMAL_2' FROM dual UNION ALL
    SELECT 'STORE-DAEGU-DONGSEONGRO', 7, 'SLOW_3' FROM dual UNION ALL
    SELECT 'STORE-DAEJEON-SHINSEGAE', 8, 'NORMAL_3' FROM dual
) stores
CROSS JOIN (
    SELECT 'SKU-CAP-BLACK-FREE' sku_id, 1 product_no FROM dual UNION ALL
    SELECT 'SKU-HOODIE-GRAY-M', 2 FROM dual UNION ALL
    SELECT 'SKU-DENIM-BLUE-M', 3 FROM dual UNION ALL
    SELECT 'SKU-CARDIGAN-IVORY-S', 4 FROM dual UNION ALL
    SELECT 'SKU-VEST-BLACK-M', 5 FROM dual UNION ALL
    SELECT 'SKU-SHIRT-WHITE-L', 6 FROM dual UNION ALL
    SELECT 'SKU-SKIRT-NAVY-S', 7 FROM dual UNION ALL
    SELECT 'SKU-BAG-BROWN-FREE', 8 FROM dual
) products;

MERGE INTO sp_daily_sale target
USING (
SELECT DATE '2026-08-19' + days.day_no AS sales_date,
       stores.store_id,
       products.sku_id,
       CASE stores.store_group
           WHEN 'FAST' THEN 4 + MOD(products.product_no + days.day_no, 3)
           WHEN 'FAST_2' THEN 5 + MOD(products.product_no + days.day_no, 2)
           WHEN 'SLOW' THEN CASE WHEN MOD(products.product_no + days.day_no, 3) = 0 THEN 1 ELSE 0 END
           WHEN 'SLOW_2' THEN 1
           WHEN 'SLOW_3' THEN CASE WHEN MOD(products.product_no + days.day_no, 2) = 0 THEN 1 ELSE 0 END
           WHEN 'NORMAL_1' THEN 3 + MOD(products.product_no + days.day_no, 2)
           WHEN 'NORMAL_2' THEN 2 + MOD(products.product_no + days.day_no, 2)
           ELSE 2 + CASE WHEN MOD(products.product_no + days.day_no, 3) = 0 THEN 1 ELSE 0 END
       END AS sold_quantity,
       'SYNTHETIC' AS source_type
FROM (
    SELECT 0 day_no FROM dual UNION ALL SELECT 1 FROM dual UNION ALL
    SELECT 2 FROM dual UNION ALL SELECT 3 FROM dual UNION ALL
    SELECT 4 FROM dual UNION ALL SELECT 5 FROM dual UNION ALL SELECT 6 FROM dual
) days
CROSS JOIN (
    SELECT 'STORE-GANGNAM' store_id, 'FAST' store_group FROM dual UNION ALL
    SELECT 'STORE-HONGDAE', 'SLOW' FROM dual UNION ALL
    SELECT 'STORE-SEONGSU', 'NORMAL_1' FROM dual UNION ALL
    SELECT 'STORE-JAMSIL', 'FAST_2' FROM dual UNION ALL
    SELECT 'STORE-YEOUIDO', 'SLOW_2' FROM dual UNION ALL
    SELECT 'STORE-BUSAN-SEOMYEON', 'NORMAL_2' FROM dual UNION ALL
    SELECT 'STORE-DAEGU-DONGSEONGRO', 'SLOW_3' FROM dual UNION ALL
    SELECT 'STORE-DAEJEON-SHINSEGAE', 'NORMAL_3' FROM dual
) stores
CROSS JOIN (
    SELECT 'SKU-CAP-BLACK-FREE' sku_id, 1 product_no FROM dual UNION ALL
    SELECT 'SKU-HOODIE-GRAY-M', 2 FROM dual UNION ALL
    SELECT 'SKU-DENIM-BLUE-M', 3 FROM dual UNION ALL
    SELECT 'SKU-CARDIGAN-IVORY-S', 4 FROM dual UNION ALL
    SELECT 'SKU-VEST-BLACK-M', 5 FROM dual UNION ALL
    SELECT 'SKU-SHIRT-WHITE-L', 6 FROM dual UNION ALL
    SELECT 'SKU-SKIRT-NAVY-S', 7 FROM dual UNION ALL
    SELECT 'SKU-BAG-BROWN-FREE', 8 FROM dual
) products
) source
ON (
    target.sales_date = source.sales_date
    AND target.store_id = source.store_id
    AND target.sku_id = source.sku_id
)
WHEN NOT MATCHED THEN INSERT (
    sales_date, store_id, sku_id, sold_quantity, source_type
) VALUES (
    source.sales_date, source.store_id, source.sku_id, source.sold_quantity, source.source_type
);

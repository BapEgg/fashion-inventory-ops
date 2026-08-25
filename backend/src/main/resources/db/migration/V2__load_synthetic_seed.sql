INSERT INTO sp_product (sku_id, product_name, category, color, size_name)
VALUES ('SKU-CAP-BLACK-FREE', 'Core Ball Cap', 'HEADWEAR', 'BLACK', 'FREE');

INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-GANGNAM', 'Gangnam Flagship', 'SEOUL');
INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-HONGDAE', 'Hongdae Store', 'SEOUL');
INSERT INTO sp_store (store_id, store_name, region)
VALUES ('STORE-SEONGSU', 'Seongsu Store', 'SEOUL');

INSERT INTO sp_inventory_snapshot (
    snapshot_date, store_id, sku_id, on_hand_quantity, reserved_quantity, source_type
) VALUES (DATE '2026-08-25', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 6, 1, 'SYNTHETIC');
INSERT INTO sp_inventory_snapshot (
    snapshot_date, store_id, sku_id, on_hand_quantity, reserved_quantity, source_type
) VALUES (DATE '2026-08-25', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 42, 2, 'SYNTHETIC');
INSERT INTO sp_inventory_snapshot (
    snapshot_date, store_id, sku_id, on_hand_quantity, reserved_quantity, source_type
) VALUES (DATE '2026-08-25', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 12, 1, 'SYNTHETIC');

INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-18', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-19', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-20', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-21', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-22', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-23', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-24', 'STORE-GANGNAM', 'SKU-CAP-BLACK-FREE', 4, 'SYNTHETIC');

INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-18', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-19', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 0, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-20', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-21', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 0, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-22', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-23', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 0, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-24', 'STORE-HONGDAE', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');

INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-18', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-19', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 2, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-20', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-21', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-22', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 2, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-23', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');
INSERT INTO sp_daily_sale (sales_date, store_id, sku_id, sold_quantity, source_type)
VALUES (DATE '2026-08-24', 'STORE-SEONGSU', 'SKU-CAP-BLACK-FREE', 1, 'SYNTHETIC');

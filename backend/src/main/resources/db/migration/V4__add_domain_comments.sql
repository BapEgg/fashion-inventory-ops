COMMENT ON TABLE sp_product IS '상품 기준정보';
COMMENT ON COLUMN sp_product.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_product.product_name IS '상품명';
COMMENT ON COLUMN sp_product.category IS '상품 카테고리';
COMMENT ON COLUMN sp_product.color IS '색상';
COMMENT ON COLUMN sp_product.size_name IS '사이즈';
COMMENT ON COLUMN sp_product.created_at IS '생성일시';

COMMENT ON TABLE sp_store IS '매장 기준정보';
COMMENT ON COLUMN sp_store.store_id IS '매장 ID';
COMMENT ON COLUMN sp_store.store_name IS '매장명';
COMMENT ON COLUMN sp_store.region IS '지역';
COMMENT ON COLUMN sp_store.created_at IS '생성일시';

COMMENT ON TABLE sp_inventory_snapshot IS '매장별 재고 스냅샷';
COMMENT ON COLUMN sp_inventory_snapshot.inventory_snapshot_id IS '재고 스냅샷 ID';
COMMENT ON COLUMN sp_inventory_snapshot.snapshot_date IS '재고 기준일';
COMMENT ON COLUMN sp_inventory_snapshot.store_id IS '매장 ID';
COMMENT ON COLUMN sp_inventory_snapshot.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_inventory_snapshot.on_hand_quantity IS '장부 재고수량';
COMMENT ON COLUMN sp_inventory_snapshot.reserved_quantity IS '예약 재고수량';
COMMENT ON COLUMN sp_inventory_snapshot.source_type IS '데이터 구분 (SYNTHETIC: 합성 데이터)';
COMMENT ON COLUMN sp_inventory_snapshot.created_at IS '생성일시';

COMMENT ON TABLE sp_daily_sale IS '매장별 일판매';
COMMENT ON COLUMN sp_daily_sale.daily_sale_id IS '일판매 ID';
COMMENT ON COLUMN sp_daily_sale.sales_date IS '판매일';
COMMENT ON COLUMN sp_daily_sale.store_id IS '매장 ID';
COMMENT ON COLUMN sp_daily_sale.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_daily_sale.sold_quantity IS '판매수량';
COMMENT ON COLUMN sp_daily_sale.source_type IS '데이터 구분 (SYNTHETIC: 합성 데이터)';
COMMENT ON COLUMN sp_daily_sale.created_at IS '생성일시';

COMMENT ON TABLE sp_analysis_run IS '재고 분석 실행';
COMMENT ON COLUMN sp_analysis_run.analysis_run_id IS '분석 실행 ID';
COMMENT ON COLUMN sp_analysis_run.analysis_date IS '분석 기준일';
COMMENT ON COLUMN sp_analysis_run.rule_version IS '규칙 버전';
COMMENT ON COLUMN sp_analysis_run.run_status IS '실행 상태 (RUNNING: 실행 중, COMPLETED: 완료, FAILED: 실패)';
COMMENT ON COLUMN sp_analysis_run.started_at IS '시작일시';
COMMENT ON COLUMN sp_analysis_run.completed_at IS '완료일시';

COMMENT ON TABLE sp_inventory_metric IS '재고 분석 지표';
COMMENT ON COLUMN sp_inventory_metric.inventory_metric_id IS '재고 지표 ID';
COMMENT ON COLUMN sp_inventory_metric.analysis_run_id IS '분석 실행 ID';
COMMENT ON COLUMN sp_inventory_metric.inventory_snapshot_id IS '재고 스냅샷 ID';
COMMENT ON COLUMN sp_inventory_metric.available_quantity IS '판매 가능수량';
COMMENT ON COLUMN sp_inventory_metric.average_daily_sales IS '일평균 판매수량';
COMMENT ON COLUMN sp_inventory_metric.coverage_days IS '재고 보유일수';
COMMENT ON COLUMN sp_inventory_metric.classification IS '재고 분류 (STOCKOUT_RISK: 품절 위험, OVERSTOCK: 과잉재고, NORMAL: 정상, NON_ACTIONABLE: 분석 제외)';
COMMENT ON COLUMN sp_inventory_metric.priority IS '품절 우선순위 (CRITICAL: 긴급, HIGH: 높음, NULL: 대상 아님)';
COMMENT ON COLUMN sp_inventory_metric.created_at IS '생성일시';

COMMENT ON TABLE sp_rebalance_recommendation IS '매장 간 재배분 추천';
COMMENT ON COLUMN sp_rebalance_recommendation.recommendation_id IS '재배분 추천 ID';
COMMENT ON COLUMN sp_rebalance_recommendation.receiver_metric_id IS '수요 매장 지표 ID';
COMMENT ON COLUMN sp_rebalance_recommendation.donor_metric_id IS '공급 매장 지표 ID';
COMMENT ON COLUMN sp_rebalance_recommendation.receiver_shortage_quantity IS '수요 매장 부족수량';
COMMENT ON COLUMN sp_rebalance_recommendation.donor_transferable_quantity IS '공급 매장 이동 가능수량';
COMMENT ON COLUMN sp_rebalance_recommendation.recommended_quantity IS '추천 이동수량';
COMMENT ON COLUMN sp_rebalance_recommendation.created_at IS '생성일시';

COMMENT ON TABLE sp_rebalance_decision IS '재배분 승인·거절';
COMMENT ON COLUMN sp_rebalance_decision.decision_id IS '재배분 결정 ID';
COMMENT ON COLUMN sp_rebalance_decision.recommendation_id IS '재배분 추천 ID';
COMMENT ON COLUMN sp_rebalance_decision.decision_status IS '결정 상태 (APPROVED: 승인, REJECTED: 거절)';
COMMENT ON COLUMN sp_rebalance_decision.selected_quantity IS '결정 이동수량';
COMMENT ON COLUMN sp_rebalance_decision.reason IS '결정 사유';
COMMENT ON COLUMN sp_rebalance_decision.actor_label IS '담당자 표시명';
COMMENT ON COLUMN sp_rebalance_decision.decided_at IS '결정일시';

-- StockPilot 도메인 Comment를 한국어 명사형으로 통일한다.
-- 허용값이 정해진 코드·상태·플래그 컬럼만 각 값의 의미를 함께 기록한다.

COMMENT ON COLUMN sp_product.launch_date IS '출시일';
COMMENT ON COLUMN sp_product.season_code IS '시즌 코드';
COMMENT ON COLUMN sp_product.sales_status IS
    '판매 상태 (PRELAUNCH: 출시 전, ACTIVE: 판매 중, CLEARANCE: 클리어런스, ENDED: 판매 종료)';

COMMENT ON COLUMN sp_store.store_type IS
    '매장 유형 (DIRECT: 직영, CONSIGNMENT: 위탁, OTHER: 기타)';
COMMENT ON COLUMN sp_store.inventory_owner_code IS '재고 소유자 코드';
COMMENT ON COLUMN sp_store.transfer_zone IS '재고 이동 권역';

COMMENT ON COLUMN sp_inventory_snapshot.snapshot_at IS '재고 스냅샷 시각';
COMMENT ON COLUMN sp_inventory_snapshot.out_of_stock_flag IS
    '품절 여부 (Y: 품절, N: 품절 아님)';
COMMENT ON COLUMN sp_inventory_snapshot.input_snapshot_version IS '입력 스냅샷 버전';

COMMENT ON COLUMN sp_daily_sale.transaction_count IS '거래 건수';
COMMENT ON COLUMN sp_daily_sale.max_transaction_quantity IS '최대 거래수량';
COMMENT ON COLUMN sp_daily_sale.average_selling_price IS '평균 판매가격';
COMMENT ON COLUMN sp_daily_sale.input_snapshot_version IS '입력 스냅샷 버전';

COMMENT ON COLUMN sp_analysis_run.input_snapshot_version IS '입력 스냅샷 버전';

COMMENT ON COLUMN sp_inventory_metric.observable_day_count IS '관측 가능일수';
COMMENT ON COLUMN sp_inventory_metric.active_week_count IS '판매 발생 주수';
COMMENT ON COLUMN sp_inventory_metric.sales_day_ratio IS '판매 발생일 비율';
COMMENT ON COLUMN sp_inventory_metric.max_daily_sales IS '일최대 판매수량';
COMMENT ON COLUMN sp_inventory_metric.median_daily_sales IS '일판매수량 중앙값';
COMMENT ON COLUMN sp_inventory_metric.mad_daily_sales IS '일판매수량 중앙절대편차';
COMMENT ON COLUMN sp_inventory_metric.max_transaction_quantity IS '최대 거래수량';
COMMENT ON COLUMN sp_inventory_metric.primary_demand_signal_type IS
    '주요 수요 신호 유형 (DATA_INSUFFICIENT: 데이터 부족, KNOWN_EVENT: 알려진 이벤트, UNEXPLAINED_SPIKE: 원인 불명 급증, INTERMITTENT: 간헐 수요, STABLE_REPEAT: 안정 반복 수요, VARIABLE: 변동 수요)';
COMMENT ON COLUMN sp_inventory_metric.demand_confidence IS
    '수요 신뢰도 (HIGH: 높음, MEDIUM: 보통, LOW: 낮음, NONE: 없음)';
COMMENT ON COLUMN sp_inventory_metric.low_demand_rate IS '낮은 시나리오 일수요율';
COMMENT ON COLUMN sp_inventory_metric.base_demand_rate IS '기준 시나리오 일수요율';
COMMENT ON COLUMN sp_inventory_metric.high_demand_rate IS '높은 시나리오 일수요율';
COMMENT ON COLUMN sp_inventory_metric.projected_available IS '예상 가용재고';
COMMENT ON COLUMN sp_inventory_metric.expected_shortage_quantity IS '예상 부족수량';
COMMENT ON COLUMN sp_inventory_metric.inventory_exception_type IS
    '재고 예외 유형 (STOCKOUT_RISK: 품절 위험, OVERSTOCK: 과잉재고, REVIEW_REQUIRED: 검토 필요, NORMAL: 정상, NON_ACTIONABLE: 조치 불가)';
COMMENT ON COLUMN sp_inventory_metric.severity IS
    '심각도 (CRITICAL: 긴급, HIGH: 높음, REVIEW: 검토)';
COMMENT ON COLUMN sp_inventory_metric.calculation_version IS '계산 버전';

COMMENT ON TABLE sp_demand_event IS '수요 이벤트';
COMMENT ON COLUMN sp_demand_event.demand_event_id IS '수요 이벤트 ID';
COMMENT ON COLUMN sp_demand_event.event_code IS '이벤트 코드';
COMMENT ON COLUMN sp_demand_event.event_type IS
    '이벤트 유형 (PROMOTION: 프로모션, PRICE_CHANGE: 가격 변경, STORE_EVENT: 매장 이벤트, OTHER: 기타)';
COMMENT ON COLUMN sp_demand_event.store_id IS '매장 ID';
COMMENT ON COLUMN sp_demand_event.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_demand_event.start_date IS '시작일';
COMMENT ON COLUMN sp_demand_event.end_date IS '종료일';
COMMENT ON COLUMN sp_demand_event.uplift_low IS '낮은 시나리오 수요 배수';
COMMENT ON COLUMN sp_demand_event.uplift_base IS '기준 시나리오 수요 배수';
COMMENT ON COLUMN sp_demand_event.uplift_high IS '높은 시나리오 수요 배수';
COMMENT ON COLUMN sp_demand_event.input_snapshot_version IS '입력 스냅샷 버전';
COMMENT ON COLUMN sp_demand_event.source_type IS
    '데이터 구분 (SYNTHETIC: 합성 데이터)';
COMMENT ON COLUMN sp_demand_event.assumption_type IS
    '가정 구분 (ASSUMPTION: 데모 가정)';
COMMENT ON COLUMN sp_demand_event.created_at IS '생성일시';

COMMENT ON TABLE sp_inbound_schedule IS '입고 예정';
COMMENT ON COLUMN sp_inbound_schedule.inbound_schedule_id IS '입고 예정 ID';
COMMENT ON COLUMN sp_inbound_schedule.inbound_reference IS '입고 참조값';
COMMENT ON COLUMN sp_inbound_schedule.store_id IS '입고 매장 ID';
COMMENT ON COLUMN sp_inbound_schedule.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_inbound_schedule.quantity IS '입고 예정수량';
COMMENT ON COLUMN sp_inbound_schedule.eta_at IS '입고 예정일시';
COMMENT ON COLUMN sp_inbound_schedule.inbound_status IS
    '입고 상태 (PLANNED: 예정, CONFIRMED: 확정, CANCELLED: 취소, RECEIVED: 입고 완료)';
COMMENT ON COLUMN sp_inbound_schedule.input_snapshot_version IS '입력 스냅샷 버전';
COMMENT ON COLUMN sp_inbound_schedule.source_type IS
    '데이터 구분 (SYNTHETIC: 합성 데이터)';
COMMENT ON COLUMN sp_inbound_schedule.created_at IS '생성일시';

COMMENT ON TABLE sp_open_transfer IS '진행 중 매장 간 이동';
COMMENT ON COLUMN sp_open_transfer.open_transfer_id IS '진행 중 이동 ID';
COMMENT ON COLUMN sp_open_transfer.transfer_reference IS '이동 참조값';
COMMENT ON COLUMN sp_open_transfer.donor_store_id IS '출고 매장 ID';
COMMENT ON COLUMN sp_open_transfer.receiver_store_id IS '입고 매장 ID';
COMMENT ON COLUMN sp_open_transfer.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_open_transfer.quantity IS '이동수량';
COMMENT ON COLUMN sp_open_transfer.eta_at IS '도착 예정일시';
COMMENT ON COLUMN sp_open_transfer.transfer_status IS
    '이동 상태 (REQUESTED: 요청, APPROVED: 승인, IN_TRANSIT: 이동 중, CANCELLED: 취소, RECEIVED: 도착 완료)';
COMMENT ON COLUMN sp_open_transfer.input_snapshot_version IS '입력 스냅샷 버전';
COMMENT ON COLUMN sp_open_transfer.source_type IS
    '데이터 구분 (SYNTHETIC: 합성 데이터)';
COMMENT ON COLUMN sp_open_transfer.created_at IS '생성일시';

COMMENT ON TABLE sp_store_transfer_route IS '매장 간 재고 이동 경로';
COMMENT ON COLUMN sp_store_transfer_route.route_id IS '이동 경로 ID';
COMMENT ON COLUMN sp_store_transfer_route.donor_store_id IS '출고 매장 ID';
COMMENT ON COLUMN sp_store_transfer_route.receiver_store_id IS '입고 매장 ID';
COMMENT ON COLUMN sp_store_transfer_route.active_flag IS
    '사용 여부 (Y: 사용, N: 미사용)';
COMMENT ON COLUMN sp_store_transfer_route.owner_override_flag IS
    '소유자 예외 허용 여부 (Y: 허용, N: 미허용)';
COMMENT ON COLUMN sp_store_transfer_route.lead_time_days IS '이동 소요일수';
COMMENT ON COLUMN sp_store_transfer_route.minimum_quantity IS '최소 이동수량';
COMMENT ON COLUMN sp_store_transfer_route.package_multiple IS '포장 단위수량';
COMMENT ON COLUMN sp_store_transfer_route.maximum_quantity IS '최대 이동수량';
COMMENT ON COLUMN sp_store_transfer_route.input_snapshot_version IS '입력 스냅샷 버전';
COMMENT ON COLUMN sp_store_transfer_route.assumption_type IS
    '가정 구분 (ASSUMPTION: 데모 가정)';
COMMENT ON COLUMN sp_store_transfer_route.created_at IS '생성일시';

COMMENT ON TABLE sp_store_sku_policy IS '매장 상품별 재고 정책';
COMMENT ON COLUMN sp_store_sku_policy.store_sku_policy_id IS '매장 상품 정책 ID';
COMMENT ON COLUMN sp_store_sku_policy.store_id IS '매장 ID';
COMMENT ON COLUMN sp_store_sku_policy.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_store_sku_policy.display_minimum IS '최소 진열수량';
COMMENT ON COLUMN sp_store_sku_policy.safety_stock IS '안전재고수량';
COMMENT ON COLUMN sp_store_sku_policy.maximum_capacity IS '최대 수용수량';
COMMENT ON COLUMN sp_store_sku_policy.target_coverage_days IS '목표 재고 보유일수';
COMMENT ON COLUMN sp_store_sku_policy.retained_days IS '출고 매장 보존일수';
COMMENT ON COLUMN sp_store_sku_policy.input_snapshot_version IS '입력 스냅샷 버전';
COMMENT ON COLUMN sp_store_sku_policy.assumption_type IS
    '가정 구분 (ASSUMPTION: 데모 가정)';
COMMENT ON COLUMN sp_store_sku_policy.created_at IS '생성일시';

COMMENT ON TABLE sp_metric_quality_flag IS '재고 지표 품질 플래그';
COMMENT ON COLUMN sp_metric_quality_flag.metric_quality_flag_id IS '품질 플래그 ID';
COMMENT ON COLUMN sp_metric_quality_flag.inventory_metric_id IS '재고 지표 ID';
COMMENT ON COLUMN sp_metric_quality_flag.flag_code IS
    '품질 플래그 (OOS_CENSORED: 품절 검열, STALE_INVENTORY: 오래된 재고 정보, MISSING_INBOUND: 입고 정보 누락, INCOMPLETE_EVENT_DATA: 이벤트 정보 불완전)';
COMMENT ON COLUMN sp_metric_quality_flag.created_at IS '생성일시';

COMMENT ON COLUMN sp_rebalance_recommendation.route_id IS '이동 경로 ID';
COMMENT ON COLUMN sp_rebalance_recommendation.candidate_status IS
    '후보 상태 (ELIGIBLE: 실행 가능, REJECTED: 탈락)';
COMMENT ON COLUMN sp_rebalance_recommendation.candidate_version IS '후보 버전';
COMMENT ON COLUMN sp_rebalance_recommendation.recommendation_mode IS
    '추천 방식 (RECOMMENDED: 추천, COMPARISON_ONLY: 비교 전용, NONE: 추천 없음)';
COMMENT ON COLUMN sp_rebalance_recommendation.receiver_shortage_quantity IS '입고 매장 부족수량';
COMMENT ON COLUMN sp_rebalance_recommendation.donor_transferable_quantity IS '출고 매장 이동 가능수량';
COMMENT ON COLUMN sp_rebalance_recommendation.recommended_quantity IS '추천 이동수량';
COMMENT ON COLUMN sp_rebalance_recommendation.projected_receiver_at_arrival IS '도착 시점 입고 매장 예상재고';
COMMENT ON COLUMN sp_rebalance_recommendation.projected_donor_at_dispatch IS '출고 시점 출고 매장 예상재고';
COMMENT ON COLUMN sp_rebalance_recommendation.receiver_capacity_remaining IS '입고 매장 잔여 수용수량';
COMMENT ON COLUMN sp_rebalance_recommendation.evaluated_at IS '후보 평가일시';

COMMENT ON TABLE sp_candidate_reason IS '후보 평가 사유';
COMMENT ON COLUMN sp_candidate_reason.candidate_reason_id IS '후보 사유 ID';
COMMENT ON COLUMN sp_candidate_reason.recommendation_id IS '재배분 추천 ID';
COMMENT ON COLUMN sp_candidate_reason.reason_code IS
    '후보 사유 (OWNER_MISMATCH: 재고 소유자 불일치, ROUTE_NOT_ALLOWED: 이동 경로 미허용, LEAD_TIME_TOO_LONG: 이동시간 초과, INBOUND_ALREADY_COVERS: 입고 예정으로 부족 해소, NO_TRANSFERABLE_STOCK: 이동 가능재고 없음, DISPLAY_MINIMUM_VIOLATION: 최소 이동수량 미충족, CAPACITY_EXCEEDED: 입고 매장 수용량 초과, PENDING_TRANSFER_CONFLICT: 진행 중 이동과 충돌)';
COMMENT ON COLUMN sp_candidate_reason.reason_order IS '사유 표시순서';
COMMENT ON COLUMN sp_candidate_reason.created_at IS '생성일시';

COMMENT ON TABLE sp_rebalance_scenario IS '재배분 시나리오 결과';
COMMENT ON COLUMN sp_rebalance_scenario.scenario_id IS '시나리오 ID';
COMMENT ON COLUMN sp_rebalance_scenario.recommendation_id IS '재배분 추천 ID';
COMMENT ON COLUMN sp_rebalance_scenario.scenario_type IS
    '시나리오 유형 (NO_ACTION: 이동 없음, CONSERVATIVE: 보수적, BASE: 기준, AGGRESSIVE: 공격적)';
COMMENT ON COLUMN sp_rebalance_scenario.demand_rate IS '시나리오 일수요율';
COMMENT ON COLUMN sp_rebalance_scenario.scenario_quantity IS '시나리오 이동수량';
COMMENT ON COLUMN sp_rebalance_scenario.package_multiple IS '포장 단위수량';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_before_available IS '이동 전 입고 매장 가용재고';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_after_available IS '이동 후 입고 매장 가용재고';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_before_coverage IS '이동 전 입고 매장 재고 보유일수';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_after_coverage IS '이동 후 입고 매장 재고 보유일수';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_risk_code IS
    '입고 매장 위험 유형 (STOCKOUT_RISK: 품절 위험, OVERSTOCK: 과잉재고, REVIEW_REQUIRED: 검토 필요, NORMAL: 정상, NON_ACTIONABLE: 조치 불가)';
COMMENT ON COLUMN sp_rebalance_scenario.donor_before_available IS '이동 전 출고 매장 가용재고';
COMMENT ON COLUMN sp_rebalance_scenario.donor_after_available IS '이동 후 출고 매장 가용재고';
COMMENT ON COLUMN sp_rebalance_scenario.donor_before_coverage IS '이동 전 출고 매장 재고 보유일수';
COMMENT ON COLUMN sp_rebalance_scenario.donor_after_coverage IS '이동 후 출고 매장 재고 보유일수';
COMMENT ON COLUMN sp_rebalance_scenario.donor_risk_code IS
    '출고 매장 위험 유형 (STOCKOUT_RISK: 품절 위험, OVERSTOCK: 과잉재고, REVIEW_REQUIRED: 검토 필요, NORMAL: 정상, NON_ACTIONABLE: 조치 불가)';
COMMENT ON COLUMN sp_rebalance_scenario.lead_time_days IS '이동 소요일수';
COMMENT ON COLUMN sp_rebalance_scenario.expected_arrival_at IS '도착 예정일시';
COMMENT ON COLUMN sp_rebalance_scenario.inbound_included_flag IS
    '입고 반영 여부 (Y: 반영, N: 미반영)';
COMMENT ON COLUMN sp_rebalance_scenario.warning_summary IS '경고 요약';
COMMENT ON COLUMN sp_rebalance_scenario.candidate_version IS '후보 버전';
COMMENT ON COLUMN sp_rebalance_scenario.created_at IS '생성일시';

COMMENT ON COLUMN sp_rebalance_decision.decision_sequence IS '결정 순번';
COMMENT ON COLUMN sp_rebalance_decision.decision_contract_version IS
    '결정 계약 버전 (MVP-1: MVP-1 계약, MVP-2: MVP-2 계약)';
COMMENT ON COLUMN sp_rebalance_decision.decision_status IS
    '결정 상태 (PENDING: 대기, HELD: 보류, APPROVED: 승인, REJECTED: 거절, EXPIRED: 만료)';
COMMENT ON COLUMN sp_rebalance_decision.selected_quantity IS '결정 이동수량';
COMMENT ON COLUMN sp_rebalance_decision.reason_code IS '결정 사유 코드';
COMMENT ON COLUMN sp_rebalance_decision.reason IS '결정 사유';
COMMENT ON COLUMN sp_rebalance_decision.recommendation_version IS '추천 버전';

COMMENT ON TABLE sp_transfer_draft IS '재고 이동 초안';
COMMENT ON COLUMN sp_transfer_draft.transfer_draft_id IS '재고 이동 초안 ID';
COMMENT ON COLUMN sp_transfer_draft.decision_id IS '재배분 결정 ID';
COMMENT ON COLUMN sp_transfer_draft.donor_store_id IS '출고 매장 ID';
COMMENT ON COLUMN sp_transfer_draft.receiver_store_id IS '입고 매장 ID';
COMMENT ON COLUMN sp_transfer_draft.sku_id IS '상품 SKU';
COMMENT ON COLUMN sp_transfer_draft.quantity IS '이동수량';
COMMENT ON COLUMN sp_transfer_draft.draft_status IS
    '초안 상태 (CREATED: 생성, READY: 전송 준비, SENT: 전송, ACCEPTED: 접수, REJECTED: 거절, EXPIRED: 만료)';
COMMENT ON COLUMN sp_transfer_draft.external_reference IS '외부 참조값';
COMMENT ON COLUMN sp_transfer_draft.payload_version IS '전송 데이터 버전';
COMMENT ON COLUMN sp_transfer_draft.created_at IS '생성일시';
COMMENT ON COLUMN sp_transfer_draft.updated_at IS '수정일시';

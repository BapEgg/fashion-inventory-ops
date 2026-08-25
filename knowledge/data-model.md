# Data Model and Seed Pipeline

Status: `IMPLEMENTED MVP-2 PHASE 1 DATA MODEL` — V6~V8 Oracle 검증
Approved: 2026-08-25
Last updated: 2026-08-25

## 1. 설계 목표와 현재 물리 기준선

원본 입력, 재현 가능한 Batch 결과, 후보·시나리오와 사람의 결정을 분리한다.
동일한 `(analysisDate, inputSnapshotVersion, ruleVersion)`은 하나의 논리 분석만
만들고, 새 입력이나 규칙 버전은 과거 결과와 결정 근거를 덮어쓰지 않는다.

현재 Oracle에는 다음 Migration이 실제 존재한다.

| Migration | 실제 책임 | 상태 |
|---|---|---|
| `V1` | 8개 도메인 테이블, 제약과 인덱스 | 구현·검증됨 |
| `V2` | `2026-08-25` MVP-1 Golden SYNTHETIC Seed | 구현·검증됨 |
| `V3` | Spring Batch 6 Oracle metadata | 구현·검증됨 |
| `V4` | 8개 도메인 테이블·54개 컬럼 Comment | 구현·검증됨 |
| `V5` | 한국어 레이블과 `2026-08-26` 확장 SYNTHETIC Seed | 구현·검증됨 |
| `V6` | MVP-2 호환 확장, 신규 입력·결과·결정·Draft 테이블 | 구현·검증됨 |
| `V7` | `MVP-2-GS-V1` GS-01~GS-06 SYNTHETIC 입력 | 구현·검증됨 |
| `V8` | MVP-2 허용값과 데모 경계 Oracle Comment | 구현·검증됨 |

따라서 첨부 초안의 MVP-2 `V5` 번호는 사용할 수 없다. 승인된 구현 순서는
`V6__evolve_stockpilot_mvp2_schema.sql`,
`V7__load_mvp2_synthetic_scenarios.sql`,
`V8__add_mvp2_domain_comments.sql`이며 Phase 1에서 모두 적용됐다. 기존
`V1`~`V5`는 수정하지 않았고 적용 전후 checksum이 일치했다.

## 2. 보존된 MVP-1 관계와 V6 호환 확장

```mermaid
erDiagram
    SP_PRODUCT ||--o{ SP_INVENTORY_SNAPSHOT : identifies
    SP_STORE ||--o{ SP_INVENTORY_SNAPSHOT : holds
    SP_PRODUCT ||--o{ SP_DAILY_SALE : identifies
    SP_STORE ||--o{ SP_DAILY_SALE : records
    SP_ANALYSIS_RUN ||--o{ SP_INVENTORY_METRIC : produces
    SP_INVENTORY_SNAPSHOT ||--o{ SP_INVENTORY_METRIC : evidence
    SP_INVENTORY_METRIC ||--o{ SP_REBALANCE_RECOMMENDATION : receiver
    SP_INVENTORY_METRIC ||--o{ SP_REBALANCE_RECOMMENDATION : donor
    SP_REBALANCE_RECOMMENDATION ||--o| SP_REBALANCE_DECISION : resolved_by
```

V1의 관계는 유지되며 V6 이후 현재 물리 자연키와 상태 shape는 다음과 같다.

- 재고·판매: `(date, store_id, sku_id, input_snapshot_version)` unique
- 분석: `(analysis_date, input_snapshot_version, rule_version)` unique
- 지표: `(analysis_run_id, inventory_snapshot_id)` unique
- 추천: `(receiver_metric_id, donor_metric_id)` unique, 상태·mode별 조건부 수량
- 결정: `(recommendation_id, decision_sequence)` unique. 기존 행은 `MVP-1`,
  신규 shape는 `MVP-2` 계약으로 구분

MVP-2는 이 행을 삭제하거나 과거 성공 이력과 결정을 재작성하지 않는다.

## 3. 구현된 Phase 1 ERD(MVP-2)

```mermaid
erDiagram
    SP_PRODUCT ||--o{ SP_DAILY_SALE : sold_as
    SP_PRODUCT ||--o{ SP_INVENTORY_SNAPSHOT : stocked_as
    SP_STORE ||--o{ SP_DAILY_SALE : records
    SP_STORE ||--o{ SP_INVENTORY_SNAPSHOT : holds
    SP_STORE ||--o{ SP_STORE_SKU_POLICY : owns
    SP_PRODUCT ||--o{ SP_STORE_SKU_POLICY : governed_by
    SP_STORE ||--o{ SP_STORE_TRANSFER_ROUTE : donor
    SP_STORE ||--o{ SP_STORE_TRANSFER_ROUTE : receiver
    SP_PRODUCT ||--o{ SP_DEMAND_EVENT : affected_by
    SP_STORE ||--o{ SP_DEMAND_EVENT : scoped_to
    SP_PRODUCT ||--o{ SP_INBOUND_SCHEDULE : inbound
    SP_STORE ||--o{ SP_INBOUND_SCHEDULE : receives
    SP_PRODUCT ||--o{ SP_OPEN_TRANSFER : moving
    SP_STORE ||--o{ SP_OPEN_TRANSFER : ships
    SP_STORE ||--o{ SP_OPEN_TRANSFER : receives
    SP_ANALYSIS_RUN ||--o{ SP_INVENTORY_METRIC : produces
    SP_INVENTORY_METRIC ||--o{ SP_METRIC_QUALITY_FLAG : has
    SP_INVENTORY_METRIC ||--o{ SP_REBALANCE_RECOMMENDATION : receiver
    SP_INVENTORY_METRIC ||--o{ SP_REBALANCE_RECOMMENDATION : donor
    SP_REBALANCE_RECOMMENDATION ||--o{ SP_CANDIDATE_REASON : explains
    SP_REBALANCE_RECOMMENDATION ||--o{ SP_REBALANCE_SCENARIO : compares
    SP_REBALANCE_RECOMMENDATION ||--o{ SP_REBALANCE_DECISION : audited_by
    SP_REBALANCE_DECISION ||--o| SP_TRANSFER_DRAFT : creates
```

`SP_OPEN_TRANSFER`와 두 다중값 자식(`SP_METRIC_QUALITY_FLAG`,
`SP_CANDIDATE_REASON`)은 원 계획서의 수식과 화면 요구를 정규화해 저장하는 데
필요해 명시적으로 추가한 논리 테이블이다. 진행 중 이동을 별도 입력으로 저장하지
않으면 `openTransferInbound/outbound` 계산을 재현할 수 없고, 플래그·탈락 사유를
단일 문자열로 압축하면 검색과 제약이 약해진다.

## 4. 입력 테이블 계약

모든 운영 입력 행은 `input_snapshot_version VARCHAR2(64)`를 가진다. MVP-2
분석에 사용된 입력은 성공 분석 후 수정하지 않고 새 버전을 만든다. 제품·매장의
표시명은 계산 근거가 아니지만 출시일, 소유권 등 계산 필드는 버전 입력과 함께
감사 스냅샷에 기록한다.

### 기존 테이블 확장

| Table | 추가/변경 컬럼 | 적용 제약 |
|---|---|---|
| `SP_PRODUCT` | `launch_date`, `season_code`, `sales_status` | `sales_status IN (PRELAUNCH, ACTIVE, CLEARANCE, ENDED)` |
| `SP_STORE` | `store_type`, `inventory_owner_code`, `transfer_zone` | 필수, 공백 금지 |
| `SP_INVENTORY_SNAPSHOT` | `snapshot_at`, `out_of_stock_flag`, `input_snapshot_version` | 버전 포함 자연키; `Y/N`; 장부·예약 수량 규칙 유지 |
| `SP_DAILY_SALE` | `transaction_count`, `max_transaction_quantity`, `average_selling_price`, `input_snapshot_version` | 모두 0 이상; 최대 거래수량 ≤ 판매수량(판매 0이면 둘 다 0) |

기존 MVP-1 행은 V6에서 `MVP-1-LEGACY`로 backfill했다. 기존 V5 Schema 업그레이드
후 제품·매장 필수 확장 컬럼의 NULL은 0건이고, legacy 재고 67행과 판매 451행의
입력 버전을 Oracle에서 확인했다.

### 신규 원본 테이블

#### `SP_DEMAND_EVENT`

| Column | Meaning |
|---|---|
| `demand_event_id` | surrogate PK |
| `event_code` | 외부 또는 합성 이벤트 ID |
| `event_type` | `PROMOTION`, `PRICE_CHANGE`, `STORE_EVENT`, `OTHER` |
| `store_id`, `sku_id` | MVP-2에서는 둘 다 필수인 명시적 범위 |
| `start_date`, `end_date` | inclusive 기간, start ≤ end |
| `uplift_low`, `uplift_base`, `uplift_high` | nullable; 모두 있으면 `0 < low <= base <= high` |
| `input_snapshot_version`, `source_type` | 입력 버전, 현재 `SYNTHETIC` |

자연키: `(event_code, store_id, sku_id, input_snapshot_version)`.

#### `SP_INBOUND_SCHEDULE`

| Column | Meaning |
|---|---|
| `inbound_schedule_id` | PK |
| `inbound_reference` | 외부/합성 입고 참조 |
| `store_id`, `sku_id`, `quantity` | 도착 매장·SKU·수량 |
| `eta_at`, `inbound_status` | ETA, `PLANNED/CONFIRMED/CANCELLED/RECEIVED` |
| `input_snapshot_version`, `source_type` | 입력 버전과 분류 |

계산에는 `CONFIRMED`만 포함한다. 불완전한 참조 행도 품질 플래그 생성을 위해
적재할 수 있으므로 nullable 허용과 계산 유효성은 구분한다.

#### `SP_OPEN_TRANSFER`

| Column | Meaning |
|---|---|
| `open_transfer_id`, `transfer_reference` | PK, 외부/합성 참조 |
| `donor_store_id`, `receiver_store_id`, `sku_id`, `quantity` | 이동 방향과 수량 |
| `eta_at`, `transfer_status` | `REQUESTED/APPROVED/IN_TRANSIT/CANCELLED/RECEIVED` |
| `input_snapshot_version`, `source_type` | 입력 버전과 분류 |

계산에는 `APPROVED`와 `IN_TRANSIT`만 포함한다. 출발·도착 매장은 달라야 한다.

#### `SP_STORE_TRANSFER_ROUTE`

| Column | Meaning |
|---|---|
| `route_id` | PK |
| `donor_store_id`, `receiver_store_id` | 방향성 있는 경로 |
| `active_flag`, `owner_override_flag` | `Y/N` |
| `lead_time_days` | 0 이상 |
| `minimum_quantity`, `package_multiple`, `maximum_quantity` | 양수, min ≤ max |
| `input_snapshot_version` | 정책 입력 버전 |

자연키: `(donor_store_id, receiver_store_id, input_snapshot_version)`.

#### `SP_STORE_SKU_POLICY`

| Column | Meaning |
|---|---|
| `store_sku_policy_id` | PK |
| `store_id`, `sku_id` | 적용 대상 |
| `display_minimum`, `safety_stock`, `maximum_capacity` | 0 이상, display + safety ≤ capacity |
| `target_coverage_days`, `retained_days` | 0 이상 |
| `input_snapshot_version` | 정책 입력 버전 |

자연키: `(store_id, sku_id, input_snapshot_version)`.

## 5. 분석 결과 테이블

### `SP_ANALYSIS_RUN`

`input_snapshot_version`을 추가하고 unique를
`(analysis_date, input_snapshot_version, rule_version)`으로 안전하게 교체한다.
기존 constraint 이름을 조회·명시해 drop/add하며 `RUNNING/COMPLETED/FAILED`를
유지한다. Spring Batch JobParameters도 같은 세 값을 사용한다.

### `SP_INVENTORY_METRIC`

기존 가용재고·평균판매·커버리지·분류 컬럼은 MVP-1 회귀를 위해 유지한다.
MVP-2에는 다음 책임을 추가한다.

- 관측 근거: `observable_day_count`, `active_week_count`, `sales_day_ratio`,
  `max_daily_sales`, `median_daily_sales`, `mad_daily_sales`,
  `max_transaction_quantity`
- 신호: `primary_demand_signal_type`, `demand_confidence`
- 수요율: `low_demand_rate`, `base_demand_rate`, `high_demand_rate`
- 재고 결과: `projected_available`, `expected_shortage_quantity`,
  `inventory_exception_type`, `severity`
- 추적: `calculation_version` 또는 분석 run FK를 통한 rule/input version

허용값은 `business-rules.md`와 동일한 Check Constraint로 제한한다. 품질 플래그는
다중값이므로 `SP_METRIC_QUALITY_FLAG(inventory_metric_id, flag_code)`의 복합 unique로
저장한다.

## 6. 후보와 시나리오

### 확정 호환안: `SP_REBALANCE_RECOMMENDATION` 확장

기존 테이블과 API identity를 보존하면서 의미를 “donor–receiver 후보 평가와
BASE 대표값”으로 넓히는 안이 승인됐다.

추가 후보 컬럼:

- `route_id`, `candidate_status` (`ELIGIBLE/REJECTED`), `candidate_version`
- `recommendation_mode` (`RECOMMENDED/COMPARISON_ONLY/NONE`)
- `projected_receiver_at_arrival`, `projected_donor_at_dispatch`
- `receiver_capacity_remaining`, `evaluated_at`

현재 양수-only 수량 Check는 상태·mode별 조건부 Check로 교체한다.
`ELIGIBLE/RECOMMENDED`는 부족·공급·BASE 대표수량을 양수로 요구한다.
`ELIGIBLE/COMPARISON_ONLY`는 부족·공급량은 양수지만 `recommended_quantity`는
`NULL`이어야 하며, `VARIABLE`에 사용한다. `REJECTED/NONE`은 수량이 nullable일
수 있다. 탈락 사유는
`SP_CANDIDATE_REASON(recommendation_id, reason_code, reason_order)`에 모두 저장하며
`(recommendation_id, reason_code)`를 unique로 둔다.

신규 Candidate 테이블로 완전히 분리하지 않는다. 기존 FK와 API 호환성을 유지하고
복수 결과는 `SP_REBALANCE_SCENARIO` 자식으로 추가한다.

### `SP_REBALANCE_SCENARIO`

| Column group | Fields |
|---|---|
| Identity | `scenario_id`, `recommendation_id`, `scenario_type` |
| Calculation | `demand_rate`, `scenario_quantity`, `package_multiple` |
| Receiver | before/after available, before/after coverage, risk code |
| Donor | before/after available, before/after coverage, risk code |
| Timing | lead days, expected arrival, inbound included flag |
| Audit | `candidate_version`, warnings, `created_at` |

`scenario_type IN (NO_ACTION, CONSERVATIVE, BASE, AGGRESSIVE)`이며
`(recommendation_id, scenario_type)` unique다. `MANUAL`은 side-effect 없는 API
계산 결과이므로 기본적으로 저장하지 않는다. 승인 시 선택한 수량과 당시 계산
근거는 결정 행에 snapshot으로 남긴다.

## 7. 결정과 이동지시 초안

### `SP_REBALANCE_DECISION`

기존 추천당 1개 unique를 제거하고 append-only 이력으로 전환한다.

| Column | Rule |
|---|---|
| `decision_sequence` | recommendation 안에서 1부터 증가, 복합 unique |
| `decision_status` | `PENDING/HELD/APPROVED/REJECTED/EXPIRED` |
| `selected_quantity` | `APPROVED`만 양수, 나머지는 `NULL` |
| `reason_code`, `reason` | 변경·보류·거절·예외 승인 시 필수 |
| `recommendation_version` | stale 검증용 |
| `actor_label`, `decided_at` | 감사 필수값 |

최신 상태는 가장 큰 `decision_sequence`로 결정한다. 상태 전이 규칙은 Java가
담당하되 DB Check가 수량·사유 shape을 방어한다.

### `SP_TRANSFER_DRAFT`

| Column | Meaning |
|---|---|
| `transfer_draft_id`, `decision_id` | PK, 승인 결정과 1:1 unique |
| `donor_store_id`, `receiver_store_id`, `sku_id`, `quantity` | ERP 초안 payload의 핵심 |
| `draft_status` | `CREATED/READY/SENT/ACCEPTED/REJECTED/EXPIRED` |
| `external_reference`, `payload_version` | nullable 외부 경계와 계약 버전 |
| `created_at`, `updated_at` | 감사 시각 |

MVP-2 구현 범위는 `CREATED` 또는 `READY`까지다. 실제 ERP 호출과 재고 차감은
하지 않는다. 한 트랜잭션에서 승인 결정과 초안을 생성하며 donor 재고 스냅샷 행을
잠그고 아직 취소되지 않은 승인 draft를 합산해 동시 초과 배분을 막는다.

## 8. 인덱스와 무결성 원칙

- 모든 날짜는 의미에 맞춰 `DATE` 또는 `TIMESTAMP WITH TIME ZONE`을 명시하고
  시간대 변환은 API 경계에서 고정한다.
- 수량은 음수를 허용하지 않고 상태별 nullable/positive 조건을 Check로 방어한다.
- 조회 인덱스는 28일 `(sku_id, store_id, date, input_snapshot_version)`, 분석 목록
  `(analysis_run_id, severity, exception_type, confidence)`, 후보
  `(receiver_metric_id, candidate_status)`를 우선한다.
- 코드 허용값은 Oracle Comment와 이 문서, Java enum에서 동일해야 한다.
- 승인 동시성은 “사전 조회 후 insert”만으로 처리하지 않고 공유 donor 근거행에
  대한 DB 잠금과 트랜잭션 내 재계산으로 검증한다.
- 기존 결정과 분석 결과를 cascade delete하지 않는다. 테스트 fixture는 고유
  rule/input version으로 격리하고 자신이 만든 데이터만 정리한다.

## 9. 구현된 Seed와 Migration

### `V6__evolve_stockpilot_mvp2_schema.sql`

1. 신규 테이블·sequence/identity·FK·Check·index 생성
2. 기존 열 추가와 legacy backfill
3. 기존 unique/check 이름을 명시적으로 교체
4. backfill 검증 후에만 `NOT NULL` 적용
5. `V1`~`V5` 데이터와 MVP-1 API 회귀 확인

### `V7__load_mvp2_synthetic_scenarios.sql`

- `GS-01`~`GS-06`의 입력을 서로 다른 store/SKU 또는 명시적 scenario code로 격리
- 28일 판매와 관측 28일+분석일 재고, 거래 특성, 이벤트, 입고, 진행 중 이동,
  경로와 정책 모두 적재
- 모든 원본 행 `source_type=SYNTHETIC`, 모든 정책값 `ASSUMPTION`
- 기존 `2026-08-25`와 `2026-08-26` 행을 삭제·수정하지 않음

### `V8__add_mvp2_domain_comments.sql`

- 신규/변경 테이블과 컬럼에 짧은 도메인 Comment
- 코드 컬럼에는 허용값과 의미를 전부 나열
- Spring Batch metadata는 도메인 Comment/ERD에서 제외

`scripts/validate-seed.ps1`은 MVP-1을 먼저 보존 검증한 뒤 CSV header, 자연키,
참조, 기간, 수량, uplift 순서, 경로 min/multiple/max와 여섯 기대 시나리오를
검증하도록 확장됐다.

## 10. 고정된 CSV/입력 파일

| File | Target |
|---|---|
| `products.csv` | `SP_PRODUCT` |
| `stores.csv` | `SP_STORE` |
| `inventory-daily.csv` | `SP_INVENTORY_SNAPSHOT` |
| `sales-daily.csv` | `SP_DAILY_SALE` |
| `demand-events.csv` | `SP_DEMAND_EVENT` |
| `inbound-schedules.csv` | `SP_INBOUND_SCHEDULE` |
| `open-transfers.csv` | `SP_OPEN_TRANSFER` |
| `transfer-routes.csv` | `SP_STORE_TRANSFER_ROUTE` |
| `store-sku-policies.csv` | `SP_STORE_SKU_POLICY` |

파일은 `data/seed/mvp2` 아래에 있고 입력 버전은 `MVP-2-GS-V1`이다. nullable과
허용값은 V6 Check, V8 Comment와 검증 스크립트에서 함께 확인한다.

## 11. 확정된 물리 설계 경계

1. 후보는 동일 소유권 또는 경로가 명시적으로 허용된 국내 매장 사이에서만
   이동 가능 상태가 된다. 위반 입력은 탈락 사유 재현을 위해 저장할 수 있다.
2. 물류센터 노드나 실제 배송 단계 없이 방향성 경로의 `lead_time_days`만 둔다.
3. 이벤트 uplift는 `SP_DEMAND_EVENT`의 low/base/high 입력 컬럼으로 받으며
   시스템이 추정하지 않는다.
4. `VARIABLE` 시나리오는 저장·응답할 수 있지만 recommendation 대표수량은
   `NULL`이며 `recommendation_mode='COMPARISON_ONLY'`로 구분한다.
5. 승인 결정은 실제 재고를 갱신하지 않고 `SP_TRANSFER_DRAFT`만 생성한다.
6. `SP_REBALANCE_RECOMMENDATION`은 유지하고
   `SP_REBALANCE_SCENARIO(recommendation_id, scenario_type)`를 자식으로 추가한다.

모든 Seed 정책값과 화면·API 설명은 `ASSUMPTION`으로 표시하며 실제 F&F 정책이나
검증된 산업 표준으로 표현하지 않는다.

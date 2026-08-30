# Archived Implemented State — Phase 3 orchestration first review

Last updated: 2026-08-27

현재 저장소에서 관찰되는 동작, 검증 결과와 미구현 범위만 유지하는 hot-state snapshot이다.

## Baseline and accepted layers

- MVP-1 기준선: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- MVP-2 immutable `V1`~`V13`, 합성 Seed/validator와 결정론적 pure Java 계산 규칙은 accepted다.
- 승인 transaction과 side-effect-free `MANUAL` application preview는 accepted다.
- Phase 3 foundation(entity/repository/factory/shared event helper)은 accepted다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. 정책은 실제 기업 정책이 아닌
  데모 `ASSUMPTION`이다.

## Phase 3 Batch input adapter — accepted

- `Mvp2InputAdapter`가 anchor+catalog+policy, 28일 inventory/sales, event, inbound,
  open transfer, route, active APPROVED draft sum을 일곱 bulk group/8개 물리 statement로
  읽는다. anchor/lane loop 안에서 SQL은 실행하지 않는다.
- anchor별 28일 evidence를 `DemandObservationWindow`로 만들며 누락·version 혼합 방지,
  미래 snapshot, 원시 행 모순과 개별/aggregate 수량 overflow를 안정적인
  `InputContractViolationException`으로 거부한다.
- 정책 부재는 `DemandAnalysisRules` 기본값을 쓰고 `TIMESTAMP WITH TIME ZONE`은
  `OffsetDateTime`으로 직접 읽어 offset을 보존한다.
- `Mvp2InputGraph`는 defensive-copy flat evidence와 함께 event/inbound의 store–SKU,
  open transfer의 donor–receiver–SKU lane, route의 donor–receiver pair immutable index를
  제공한다. map과 내부 list 모두 수정 불가능하다.
- 현재 input adapter 관련 열린 finding은 없다.

## Independent verification evidence

- Codex 직전 라운드 Oracle 표적 `Mvp2InputAdapterIT`: 10/10.
- Claude 표적(이번 라운드, 실제 실행): `EffectiveReceiverBaseRateTest` 6/6(pure),
  `Mvp2CalculationOrchestratorTest` 7/7(pure), `Mvp2BatchGoldenScenarioIT` 1/1(Oracle,
  실제 `MVP-2-GS-V1` golden 값 전체 일치, output 저장 없음, orchestrator SQL 0회 재확인).
- Oracle 전체 Backend build: **329/329**, skip 0, failures/errors 0.
- DB-free 전체 build: **329 total / 259 passed / 70 Oracle-conditioned skip**,
  failures/errors 0. `git diff --check` 통과(기존 파일의 LF/CRLF 경고만).

## Phase 3 Batch in-memory calculation — implemented, Codex 재검증 대기

- 새 `Mvp2CalculationOrchestrator`(`batch` package, pure, SQL 0회)가 `Mvp2InputGraph`를
  기존 `demand` 순수 규칙에 연결한다: anchor별 통계·plan horizon → 대표 event/signal/
  confidence/baseline → earliest-arrival lead와 canonical projection/provisional exception →
  자동 계산 가능하고 실제 부족한 receiver만 같은 SKU 다른 anchor를 donor로
  `ApprovalBasisRecalculation.calculate`(기존 그대로 재사용)로 평가 → ELIGIBLE/RECOMMENDED,
  ELIGIBLE/COMPARISON_ONLY, REJECTED/NONE과 모든 탈락 사유 보존 → 실제 eligible 유무로
  exception 재분류 후 eligible만 네 scenario 계산.
- 새 pure helper `demand.EffectiveReceiverBaseRate`: 대표 KNOWN_EVENT가 해당 route의
  도착~목표 커버리지 구간과 겹칠 때만 baseline BASE에 uplift BASE를 적용한다(§10 공유
  계약). `TransferScenarioSet`은 이미 시나리오별로 자체 적용하므로 이 helper를 쓰지 않는다.
- 새 quality flag 도출: `STALE_INVENTORY`(현재 snapshot이 analysisReferenceAt보다
  24시간 넘게 오래되었거나 관측일 현지 날짜 불일치), `MISSING_INBOUND`(수량/ETA 누락
  입고 행). `OOS_CENSORED`/`INCOMPLETE_EVENT_DATA`는 기존 계산 결과를 그대로 사용한다.
- 새 결과 타입 `Mvp2MetricResult`, `Mvp2CandidateResult`, `Mvp2CalculationResult` — JPA/
  JdbcTemplate/Spring Batch 의존성 없음, `metricsByStoreSku`/`candidatesByReceiver` 식별
  가능한 key map과 결정론적 정렬된 flat list를 함께 제공한다.
- `Mvp2BatchGoldenScenarioIT`가 실제 `MVP-2-GS-V1` seed로 adapter+orchestrator를 실행해
  (output 저장 없음) metric 12·candidate 4·scenario 8과 GS-01~06 golden 값을 모두
  정확히 확인했다. 이 과정에서 실제 버그 2건을 발견·수정했다: (1) receiver gate가
  "자동 계산 가능"만 검사해 재고가 충분한 donor도 receiver로 잘못 평가됨(9개 후보),
  (2) 그 gate가 처음엔 inbound 반영된 projection을 써서 GS-05가 candidate 자체를 못
  만듦(INBOUND_ALREADY_COVERS를 검증할 수 없음) — 둘 다 `currentAvailable` 기준의
  얕은 pre-check로 고쳤다(정식 판정은 여전히 `ApprovalBasisRecalculation`이 담당).

## Not implemented

- entity 변환과 원자적 output 저장, run transaction/retry, Job/Step wiring
- 승인·`MANUAL` event-aware effective BASE parity wiring
- MVP-2 계산/승인/MANUAL의 REST/React wiring과 실제 LLM provider adapter

다음 구현 단위와 검증 조건은 [`current-task.md`](current-task.md)가 소유한다.

## Cold evidence

- Input adapter 승인 입력 state:
  [`../archive/state/2026-08-27-phase3-input-adapter-acceptance-current-task.md`](../archive/state/2026-08-27-phase3-input-adapter-acceptance-current-task.md),
  [`../archive/state/2026-08-27-phase3-input-adapter-acceptance-implemented-state.md`](../archive/state/2026-08-27-phase3-input-adapter-acceptance-implemented-state.md)
- Input adapter 승인까지의 worklog:
  [`../archive/worklogs/2026-08-27-through-phase3-input-adapter-acceptance.md`](../archive/worklogs/2026-08-27-through-phase3-input-adapter-acceptance.md)

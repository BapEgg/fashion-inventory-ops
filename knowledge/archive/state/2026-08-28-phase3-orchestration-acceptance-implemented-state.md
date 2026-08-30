# Archived Implemented State — Phase 3 orchestration acceptance

Last updated: 2026-08-28

현재 저장소에서 관찰되는 동작과 승인 상태만 유지하는 hot-state snapshot이다.

## Accepted baseline

- MVP-1 기준선: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- MVP-2 `V1`~`V13`, 합성 Seed/validator, 결정론적 pure Java 규칙, 승인 transaction,
  side-effect-free `MANUAL`, Phase 3 foundation은 accepted다.
- Phase 3 `Mvp2InputAdapter`의 immutable graph와 8-statement/no-loop 계약은 accepted다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. 정책은 데모 `ASSUMPTION`이다.

## Phase 3 in-memory orchestration — findings fixed, Codex 재검증 대기

- `Mvp2CalculationOrchestrator`가 input graph를 통계·대표 event·baseline·projection·후보·
  scenario 순수 규칙에 연결하며 자체 SQL과 DB write는 없다.
- `EffectiveReceiverBaseRate`와 immutable 결과 record 세 종류가 추가됐다.
- Oracle Golden fixture에서 metric 12, candidate 4, scenario 8과 GS-01~06 제시값(GS-06은
  이제 필터 없는 정확한 전체 사유 목록) 및 adapter+orchestrator SQL 8회를 재현한다.

### Findings closed this round

1. **confidence downgrade**: orchestrator에서만 계산되는 `STALE_INVENTORY`/`MISSING_INBOUND`가
   이제 `DATA_INSUFFICIENT/NONE`이 아닌 한 confidence를 `LOW`로 낮추고, 이 값이 gate·
   scenario·provisional exception(`REVIEW_REQUIRED`)에 그대로 전파된다.
2. **open-transfer-aware receiver gate**: shortage gate가 이제 `APPROVED/IN_TRANSIT` open
   transfer 양방향을 반영하되 confirmed inbound는 계속 제외한다(INBOUND_ALREADY_COVERS는
   여전히 lane 단계에서 발견). 음수(invalid) projection은 receiver를 완전히 제외한다.
3. **도착 시점 projection**: `projectedReceiverAtArrival`이 이제
   `receiverAtArrivalWithoutNewTransfer(effectiveReceiverBaseRate, route.leadTimeDays())`를
   쓴다(이전엔 도착 전 snapshot 값을 잘못 넣었다).
4. **immutable iteration order**: `Mvp2InputGraph`/`Mvp2CalculationResult`의 identified map과
   `Mvp2MetricResult.qualityFlags`가 `Map.copyOf`/`Set.copyOf` 대신
   `Collections.unmodifiableMap(LinkedHashMap)`/`EnumSet` 기반으로 store/SKU 삽입 순서와
   enum 선언 순서를 보존한다.
5. **exact aggregate arithmetic**: open transfer·confirmed inbound 합계가 `long`으로
   누적된 뒤 aggregate 자체를 범위 검사해(`safeIntSum`) overflow 시
   `InputContractViolationException`을 던진다(개별 행이 아닌 합계 단위 검사).

## Independent verification evidence

- Codex 직전 라운드: `EffectiveReceiverBaseRateTest` 6/6, `Mvp2CalculationOrchestratorTest`
  7/7, `Mvp2BatchGoldenScenarioIT` 1/1.
- Claude 표적(이번 라운드, 실제 실행): `Mvp2CalculationOrchestratorTest` **16/16**(9개 신규:
  confidence downgrade가 candidate를 막고 REVIEW_REQUIRED로 보내는지, approved inbound/
  outbound open transfer가 gate를 각각 올바르게 배제/포함하는지, invalid projection 배제,
  도착 시점 projection 값, enum 순서·불변성, 두 aggregate overflow), `EffectiveReceiverBaseRateTest`
  6/6과 `Mvp2BatchGoldenScenarioIT` 1/1(GS-06 정확한 전체 목록으로 강화)이 다섯 수정에
  영향받지 않고 그대로 통과함을 재확인했다.
- Oracle 전체 Backend build: **338/338**, skip 0, failures/errors 0.
- DB-free 전체 build: **338 total / 268 passed / 70 conditional skip**,
  failures/errors 0. `git diff --check` 통과(기존 파일의 LF/CRLF 경고만).

## Not implemented

- entity 변환과 원자적 output 저장, run transaction/retry, Job/Step wiring
- 승인·`MANUAL` event-aware effective BASE parity wiring
- MVP-2 REST/React wiring과 실제 LLM provider adapter

## Cold evidence

- 첫 구현/리뷰 state:
  [`../archive/state/2026-08-28-phase3-orchestration-first-review-current-task.md`](../archive/state/2026-08-28-phase3-orchestration-first-review-current-task.md),
  [`../archive/state/2026-08-28-phase3-orchestration-first-review-implemented-state.md`](../archive/state/2026-08-28-phase3-orchestration-first-review-implemented-state.md)
- 첫 리뷰까지의 worklog:
  [`../archive/worklogs/2026-08-28-through-phase3-orchestration-first-review.md`](../archive/worklogs/2026-08-28-through-phase3-orchestration-first-review.md)

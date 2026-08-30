# Implemented State

Last updated: 2026-08-28 (Batch Job/Step wiring P2 fix)

현재 저장소에서 관찰되는 accepted 동작과 미구현 범위만 유지하는 hot-state snapshot이다.

## Accepted baseline

- MVP-1 기준선: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- MVP-2 `V1`~`V13`, 합성 Seed/validator, pure Java 계산 규칙, 승인 transaction,
  side-effect-free `MANUAL`, Phase 3 foundation은 accepted다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. 정책은 데모 `ASSUMPTION`이다.

## Phase 3 Batch input and calculation — accepted

- `Mvp2InputAdapter`가 7개 bulk group/8 statements로 현재 snapshot+catalog+policy,
  28일 inventory/sales, event, inbound, open transfer, route와 active approved draft 합계를
  immutable `Mvp2InputGraph`로 읽는다. 계산 loop SQL은 없다.
- `Mvp2CalculationOrchestrator`가 통계·quality·plan horizon → representative event/signal/
  baseline → canonical projection → donor/receiver candidate → final exception과 네 scenario를
  기존 pure Java 규칙으로 결정한다.
- `STALE_INVENTORY`/`MISSING_INBOUND`는 confidence와 review gate에 반영되고,
  `APPROVED/IN_TRANSIT` open transfer가 confirmed inbound 제외 shortage gate에 반영된다.
- Candidate는 route-specific effective event BASE를 사용하며 도착 예상재고, 전체 탈락 사유,
  BASE 대표수량과 scenario를 제공한다. confirmed inbound/open-transfer 합계 overflow는
  `InputContractViolationException`으로 거부한다.
- 결과 list/map/quality flag는 immutable이며 store/SKU, donor와 enum 순서를 보존한다.
- 현재 input adapter와 in-memory calculation 관련 열린 finding은 없다.

## Phase 3 Batch output persistence — accepted

- `Mvp2CalculationOrchestrator`가 metric마다 raw BASE rate 기준
  `expectedShortageQuantity`(nullable Long)를 계산해 `Mvp2MetricResult`에 채운다.
  `SpInventoryMetric`의 MVP-2 constructor는 이 값을 그대로 저장한다(재계산 없음).
- `Mvp2RunLifecycleService`/`Mvp2RunLifecycleTransactions`가 자연키
  `(analysisDate, inputSnapshotVersion, ruleVersion)` claim/재시도(신규 insert →
  경합 시 `resolveCreateRace`)와 `FAILED`→`RUNNING` 재시작, `RUNNING`/`COMPLETED`
  no-op 판정을 각각 `REQUIRES_NEW` transaction으로 구현한다.
- `Mvp2AtomicOutputWriter.writeAndComplete`가 run lock/검증 → snapshot bulk read →
  partial-result guard → metric/quality flag → candidate/reason/scenario → run
  완료 전이까지 하나의 `@Transactional`로 원자적으로 쓴다. structural validation만
  하고 candidate eligibility/이유/scenario 수량은 재판정하지 않는다.
- flat/index 구조 검증은 metric key→value 대응과 candidate receiver-key 배치까지 확인해
  손상된 index가 snapshot 조회나 insert 전에 거부된다.
- `Mvp2AnalysisExecutor.execute`가 claim → input adapter → 순수 calculate → writer를
  호출하는 Batch 타입 없는 진입점이다. claim 이후 실패는 별도 transaction으로
  `markFailed`를 기록한 뒤 원래 예외를 다시 던진다(기록 자체 실패는 suppressed).
- 실제 두 스레드/커넥션의 동일 natural-key claim은 같은 run id로 수렴하며 정확히 한
  caller만 `STARTED`, 다른 caller는 `ALREADY_RUNNING`을 받는다.
- Spring Batch Job/Step wiring은 아직 없다 -- 이 executor를 호출하는 진입점이 없다.

## MVP-2 Batch Job/Step wiring — production code accepted; concurrency-test P2 fix pending re-review

- `Mvp2AnalysisJobParameters`가 `analysisDate`/`inputSnapshotVersion`/`ruleVersion` 세 개만
  허용하는 유일한 JobParameters 변환·검증 경계다. `Mvp2AnalysisJobParametersValidator`는
  여기 위임한다.
- `Mvp2AnalysisTasklet`은 `Mvp2AnalysisExecutor.execute`만 호출하고 예외를 그대로 전파하며,
  domain no-op(`alreadyCompleted=true`)일 때만 exit status를 `ALREADY_COMPLETED`로 설정한다.
- `Mvp2AnalysisJobConfig`가 `mvp2AnalysisJob`/`mvp2AnalysisStep`을 추가했다. Step은 전용
  `ResourcelessTransactionManager`를 써서 executor의 `REQUIRES_NEW`/단일 output transaction
  경계를 Step 트랜잭션이 감싸지 않는다. 기존 `InventoryAnalysisJobConfig`/`AnalysisRunService`는
  `@Qualifier`로 명시해 두 Job/Step bean 공존 시 타입 모호성을 없앴다.
- 자연키 재시도는 Spring Batch `JobRepository`에 위임한다. 실패한 JobInstance는 새
  JobExecution으로 재시작하고 완료된 인스턴스는 `JobInstanceAlreadyCompleteException`, 이미
  존재하는 실행 중 인스턴스는 `JobExecutionAlreadyRunningException`으로 거부한다.
- Oracle에서 신규 JobInstance를 완전히 동시에 처음 생성하면 Spring Batch JDBC DAO의
  SERIALIZABLE 충돌(`ORA-08177`)이 원시 persistence 예외로 노출될 수 있다는 관찰은 유효하다.
  이를 향후 REST launcher의 안정적 오류 계약으로 정규화하는 작업은 이번 Job/Step 범위 밖이다.
- 동시성 테스트는 이제 `@MockitoSpyBean`으로 감싼 `Mvp2AnalysisExecutor`와 latch 두 개로 첫
  Job의 `STARTED` JobExecution 행이 실제로 커밋된 뒤에만 두 번째 launcher를 실행해, 첫 번째
  insert 자체의 경합이 아니라 "이미 RUNNING인 JobInstance"에 대한 정확한
  `JobExecutionAlreadyRunningException` 거부만 증명한다.
- 공식 Golden triple `(2026-09-30, MVP-2-GS-V1, MVP-2)`는 `Mvp2AnalysisJobGoldenScenarioIT`가
  production Job으로 실행하고 절대 삭제하지 않는 유일한 소유자다.
- REST/React/scheduler/stale-run 복구는 이번 범위에서 제외했다(계획대로).

## Independent verification evidence

- Codex pure targets (input/calculation round): `EffectiveReceiverBaseRateTest` 6/6,
  `Mvp2CalculationOrchestratorTest` 16/16(→ Codex가 map key iteration/nested candidate-list
  불변성 단정을 추가한 뒤 22/22).
- Codex Oracle `Mvp2BatchGoldenScenarioIT` (input/calculation round): 1/1; metric 12,
  candidate 4, scenario 8, GS-01~06 핵심값, GS-06 exact reasons와 SQL 8회 통과.
- Codex Oracle 전체 Backend build (input/calculation round): 338/338, skip 0.
- Codex DB-free 전체 build (input/calculation round): 338 total / 268 passed / 70 skip.
- Codex output-persistence targets: pure calculation/executor/writer validation 28/28;
  Oracle lifecycle 7/7, atomicity 1/1, persisted Golden 1/1, entity mapping 8/8.
- Codex Oracle 전체 Backend build: 359/359, skip 0, failures/errors 0.
- Codex DB-free 강제 재실행: 359 total / 280 passed / 79 conditional skip,
  failures/errors 0.
- Codex Batch wiring targets: pure parameter/Tasklet 15/15, Oracle config/Golden/retry-concurrency
  7/7; production code의 parameter, Tasklet, resourceless Step, retry와 Golden output finding은 없다.
- Codex Oracle 전체 Backend build: **380/380**, skip 0, failures/errors 0;
  `InventoryAnalysisGoldenScenarioIT` 포함 MVP-1 회귀 없음.
- Codex DB-free 전체 build: **380 total / 295 passed / 85 conditional skip**,
  failures/errors 0.
- `git diff --check`: exit 0; 기존 tracked 파일의 LF/CRLF warning만 존재한다.
- Claude P2 수정: `Mvp2AnalysisJobRetryAndConcurrencyOracleIT`를 latch 기반 결정론적 검증으로
  교체하고 Oracle에서 5회 강제 재실행해 매번 통과(정확한 예외 타입, JobInstance/JobExecution/
  domain run 각각 1개)를 확인했다. Oracle 전체 380/380, DB-free 380 total/295 passed/85 skip,
  failures/errors 0. Codex 재검증 전이다.

## Not implemented

- MVP-2 REST/React wiring과 실제 LLM provider adapter
- 승인·`MANUAL` event-aware effective BASE parity wiring
- 운영 scheduler, stale-`RUNNING` 자동 회수/timeout

## Cold evidence

- Orchestration 승인 state:
  [`../archive/state/2026-08-28-phase3-orchestration-acceptance-current-task.md`](../archive/state/2026-08-28-phase3-orchestration-acceptance-current-task.md),
  [`../archive/state/2026-08-28-phase3-orchestration-acceptance-implemented-state.md`](../archive/state/2026-08-28-phase3-orchestration-acceptance-implemented-state.md)
- 승인까지의 worklog:
  [`../archive/worklogs/2026-08-28-through-phase3-orchestration-acceptance.md`](../archive/worklogs/2026-08-28-through-phase3-orchestration-acceptance.md)


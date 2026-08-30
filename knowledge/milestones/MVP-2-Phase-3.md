# MVP-2 Phase 3 Batch Checkpoint

Status: `ACCEPTED KNOWLEDGE BASELINE`
Cutoff: 2026-08-28
Checkpoint created: 2026-08-28

이 문서는 MVP-2 Phase 3의 입력 적재 이후 분석 실행 경계에서 최종 구현·검증된 상태를
고정한다. 세션별 finding과 수정 과정은 archive에 보존하며, 이 checkpoint는 Git tag나
release 생성을 의미하지 않는다.

## Delivered scope

- Oracle의 버전 입력을 고정 수 bulk query로 읽는 `Mvp2InputAdapter`
- immutable graph에서 동작하는 결정론적 Java 수요·품질·재고 예외·공급 후보·시나리오 계산
- 분석 run claim/fail/retry와 전체 결과의 원자적 Oracle persistence
- 세 identifying parameter를 사용하는 `mvp2AnalysisJob`/`mvp2AnalysisStep`
- 기존 MVP-1 Job/API와 공존하는 JDBC Spring Batch metadata 구성
- 공식 MVP-2 Golden Scenario의 production Job 실행과 영속 결과 검증

## Durable behavior and invariants

- 실행 natural key는 정확히 `(analysisDate: LocalDate, inputSnapshotVersion: String,
  ruleVersion: String)`이며 세 Spring Batch parameter 모두 identifying이다.
- 현재 rule version은 `DemandAnalysisRules.RULE_VERSION`인 `MVP-2`만 허용한다. parameter를
  자동 trim·변환·기본값 처리하지 않는다.
- input adapter는 7개 bulk group/8 SQL statements로 28일 inventory/sales, catalog/policy,
  event, inbound, open transfer, route와 active approved draft 합계를 읽는다. 계산 loop SQL은 없다.
- 수요 신호, confidence, 품질 flag, projected inventory, exception, candidate eligibility,
  탈락 사유와 scenario 수량은 순수 Java가 결정한다. AI는 이 결과를 변경하지 않는다.
- 결과 list/map과 중첩 candidate 구조는 immutable이며 store/SKU, donor와 enum 순서를 보존한다.
- `Mvp2RunLifecycleService`는 별도 `REQUIRES_NEW` transaction으로 run을 claim하고 실패를
  기록한다. `Mvp2AtomicOutputWriter`는 모든 metric/flag/candidate/reason/scenario와 COMPLETED
  전이를 하나의 transaction으로 저장해 부분 결과를 남기지 않는다.
- 같은 domain triple의 FAILED run은 같은 run id로 재시작하고, RUNNING은 거부하며,
  COMPLETED는 output을 다시 쓰지 않는다.
- MVP-2 Step은 non-bean `ResourcelessTransactionManager`를 사용한다. Step transaction이
  executor 내부의 lifecycle/output JPA transaction 경계를 확대하지 않는다.
- 실패한 Batch JobInstance는 새 JobExecution으로 재시작한다. 완료된 인스턴스는
  `JobInstanceAlreadyCompleteException`, 이미 실행 중인 인스턴스는
  `JobExecutionAlreadyRunningException`으로 tasklet 전에 거부된다.
- 신규 JobInstance를 Oracle에서 완전히 동시에 처음 생성할 때 JDBC DAO의 SERIALIZABLE
  충돌(`ORA-08177`)이 노출될 수 있다. 향후 REST launcher가 안정적인 서비스 오류로 정규화한다.
- 공식 Golden triple `(2026-09-30, MVP-2-GS-V1, MVP-2)`은 production Job과 domain 결과를
  함께 보존하며 다른 테스트가 삭제·변경하지 않는다.

## Compatibility boundary

- `V1`~`V13` Migration과 기존 합성 Seed를 수정하지 않는다. Schema 확장은 새 Migration만 쓴다.
- `inventoryAnalysisJob`, MVP-1 endpoint와 `2026-08-25` Golden Scenario를 보존한다.
- 계산·entity 변환·retry·transaction 규칙을 Tasklet, REST, React 또는 AI prompt에 복제하지 않는다.
- 실제 재고 차감, 외부 ERP 이동 실행과 자동 승인은 수행하지 않는다.

## Final verification evidence

- Codex pure parameter/Tasklet targets: 15/15.
- Codex Oracle config/Golden/retry-concurrency targets: 7/7.
- 수정된 retry/concurrency Oracle IT: Codex 강제 재실행 3회, 매회 2/2. 정확한
  `JobExecutionAlreadyRunningException`, executor 1회, JobInstance/JobExecution/domain run
  cardinality 1과 최종 COMPLETED를 확인했다.
- Codex Oracle 전체 Backend build: 380/380, skip 0, failures/errors 0.
- Codex DB-free 전체 Backend build: 380 total, 295 passed, 85 conditional skip,
  failures/errors 0.
- 기존 MVP-1 Golden Job 회귀, JDBC `SimpleJobRepository`, BATCH parameter 타입·identifying,
  MVP-2 Golden run 1/metric 12/flag 1/candidate 4/reason 3/scenario 8을 실제 Oracle에서 검증했다.

## Deferred at checkpoint

- MVP-2 REST/React application wiring과 실제 LLM provider adapter
- 승인·`MANUAL` event-aware effective BASE parity wiring
- 운영 scheduler, stale RUNNING 자동 회수/timeout과 첫-insert 경쟁 오류 정규화
- 인증과 외부 ERP/WMS/TMS 연동

## Provenance

- 압축 전 current task:
  [`../archive/state/2026-08-28-phase3-batch-acceptance-current-task.md`](../archive/state/2026-08-28-phase3-batch-acceptance-current-task.md)
- 압축 전 implemented state:
  [`../archive/state/2026-08-28-phase3-batch-acceptance-implemented-state.md`](../archive/state/2026-08-28-phase3-batch-acceptance-implemented-state.md)
- 압축 전 active worklog:
  [`../archive/worklogs/2026-08-28-through-phase3-batch-acceptance.md`](../archive/worklogs/2026-08-28-through-phase3-batch-acceptance.md)

원인 조사나 감사가 아닌 일반 Resume에서는 archive 대신 이 checkpoint를 읽는다.

# Current Task

Status: P2 concurrency-test finding corrected by Claude; awaiting Codex re-review
Current role: Codex verification/review
Last updated: 2026-08-28

## Goal

Codex의 P2 finding("concurrency IT가 이미 실행 중인 JobInstance 거부를 결정론적으로 증명하지
못한다")을 해결했다. Codex는 아래 수정이 실제로 명세를 만족하는지 재검증한다.

## 수정 요약

- `Mvp2AnalysisJobRetryAndConcurrencyOracleIT`의 동시성 테스트를 통째로 교체했다:
  `twoConcurrentLaunchersOfTheSameFreshTripleResolveToExactlyOneRunningExecution` (넓은
  `catch (Exception)` 허용) → `aSecondLauncherAgainstAGenuinelyRunningJobInstanceIsRejectedWithJobExecutionAlreadyRunning`.
- `@MockitoSpyBean private Mvp2AnalysisExecutor executor`를 추가하고, 이 테스트가 소유한
  triple에만 `doAnswer`를 건다: 첫 launcher가 실제 `execute` 안에 진입하면 `entered` latch를
  내리고 `release` latch에서 대기한 뒤 `callRealMethod()`로 실제 로직을 그대로 수행한다.
- 첫 launch는 워커 스레드에서 `jobOperator.start(...)`로 시작하고, 메인 스레드는 `entered`를
  기다린 뒤(= Step의 `STARTED` JobExecution 행이 이미 커밋된 뒤) 두 번째 `jobOperator.start(...)`를
  직접 호출한다. 두 번째 호출은 `assertThrows(JobExecutionAlreadyRunningException.class, ...)`로
  정확한 예외 타입만 허용한다. `finally`에서 항상 `release`를 내려 deadlock을 막는다.
- 첫 Job이 `COMPLETED`인지, executor가 이 triple에 정확히 1회 호출됐는지
  (`verify(executor, times(1))`), Oracle의 `BATCH_JOB_INSTANCE`/`BATCH_JOB_EXECUTION`이 이
  triple에 대해 각각 정확히 1개인지, domain run이 `COMPLETED`인지를 모두 확인한다.
- production Job/Step/executor, Migration, MVP-1, REST 오류 계약은 변경하지 않았다. retry
  테스트(`aFailedJobExecutionRestartsUnderTheSameJobInstanceAndThenRejectsFurtherRelaunch`)와
  FK 역순 cleanup은 그대로 유지했다.
- sleep은 사용하지 않았다 -- latch 두 개로만 순서를 통제한다.

## 검증 결과 (Claude, 실제 실행)

- `Mvp2AnalysisJobRetryAndConcurrencyOracleIT`를 Oracle에 대해 **5회 강제 재실행**, 매번
  2/2 통과(정확히 `JobExecutionAlreadyRunningException`, JobInstance/JobExecution/domain run
  각각 1개, 첫 Job `COMPLETED`) -- flaky 없음.
- Oracle 전체 Backend build: **380/380**, skip 0, failures/errors 0.
- DB 없는 전체 build: **380 total / 295 passed / 85 conditional skip**, failures/errors 0.
- `git diff --check`: exit 0; 기존 tracked 파일의 LF/CRLF warning만 존재한다.

## Next verifiable action

Codex가 수정된 concurrency IT를 재검증하고, finding이 해소됐으면 이 Batch Job/Step wiring
단위 전체를 accepted로 표시한 뒤 다음 범위(REST/React/LLM wiring 등)를 명세한다.


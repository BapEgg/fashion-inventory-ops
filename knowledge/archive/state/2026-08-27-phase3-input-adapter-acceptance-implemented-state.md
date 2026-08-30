# Archived Implemented State — Phase 3 input adapter acceptance input

Last updated: 2026-08-27

현재 저장소에서 관찰되는 동작, 검증 결과와 열린 결함만 유지하는 hot-state snapshot이다.

## Baseline and accepted layers

- MVP-1 기준선: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- MVP-2 immutable `V1`~`V13`, 합성 Seed/validator와 결정론적 pure Java 계산 규칙은 accepted다.
- 승인 transaction과 side-effect-free `MANUAL` application preview는 accepted다.
- Phase 3 foundation(entity/repository/factory/shared event helper)은 accepted다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. 정책은 실제 기업 정책이 아닌
  데모 `ASSUMPTION`이다.

## Phase 3 Batch input adapter — findings fixed, Codex 재검증 대기

- `com.bapegg.stockpilot.batch.Mvp2InputAdapter`가 anchor+catalog+policy, inventory/sales
  history, event, inbound, open transfer, route, active APPROVED draft sum을 8개 물리
  statement로 bulk read한다. anchor/lane loop 안에서 SQL은 실행하지 않는다.
- anchor별 28일 inventory/sales를 `DemandObservationWindow`로 만들고, 누락·원시 행 모순과
  개별 주요 수량 overflow를 `InputContractViolationException`으로 변환한다.
- 정책 행이 없으면 `DemandAnalysisRules`의 승인된 기본값을 사용한다.
- `TIMESTAMP WITH TIME ZONE`은 `OffsetDateTime`으로 직접 읽어 offset을 보존한다.
- `Mvp2InputGraph`는 flat list와 함께 네 개의 immutable indexed map
  (`eventsByStoreSku`, `inboundByStoreSku`, `openTransfersByLane`, `routesByStorePair`)을
  제공한다. adapter는 output entity를 저장하지 않는다.

### Findings closed this round

1. **future snapshot 독립 탐지**: history query가 상한을 없애 같은 버전의 미래 행도
   같은 statement로 보이며, anchor 처리에서 missing-day 검사 전에 `snapshot_date > analysisDate`
   행과 anchor 자신의 `snapshotAt > analysisReferenceAt`(`analysisDate+1일 00:00 Asia/Seoul`)을
   각각 거부한다. 오래된 현재 snapshot은 여전히 후속 `STALE_INVENTORY` 몫이다.
2. **route NUMBER checked conversion**: `lead_time_days`/`minimum_quantity`/`package_multiple`/
   `maximum_quantity`가 다른 수량과 동일하게 `getLong` → `safeInt` 경로를 쓴다.
3. **draft 합계 overflow 차단**: `SUM(quantity)`를 `safeInt`로 검증한 뒤에만 graph에 넣는다
   (map 값 타입은 `Long` 유지).
4. **key-indexed graph**: 새 `Mvp2StoreSkuKey`/`Mvp2LaneKey`/`Mvp2StorePairKey`와 adapter의
   in-memory `groupBy` helper(새 query 없음)로 네 map을 만들고 `Mvp2InputGraph`의 compact
   constructor에서 map과 내부 list를 모두 깊은 불변으로 만든다.

## Independent verification evidence

- Codex 직전 라운드 Oracle 표적 `Mvp2InputAdapterIT`: 6/6.
- Claude 표적(이번 라운드, 실제 실행): `Mvp2InputAdapterIT` **10/10**(Oracle) — 기존 6개
  (버전 혼합 테스트는 future-date decoy가 이제 finding 1로 먼저 걸려 이름·범위를 좁힘) +
  future snapshot 독립 탐지, `snapshotAt` 미래, route overflow, draft 합계 overflow 4개
  신규. 확장된 2-lane fixture에서도 8-statement/no-loop 계측은 그대로 8이었다.
- Oracle 전체 Backend build: **315/315**, skip 0, failures/errors 0.
- DB-free 전체 build: **315 total / 246 passed / 69 Oracle-conditioned skip**,
  failures/errors 0. `git diff --check` 통과(기존 파일의 LF/CRLF 경고만).

## Not implemented

- job/tasklet 계산·영속화 orchestration과 transaction/retry
- 승인·`MANUAL` event-aware effective BASE parity wiring
- `MVP-2-GS-V1` Batch golden scenario test
- MVP-2 계산/승인/MANUAL의 REST/React wiring과 실제 LLM provider adapter

## Cold evidence

- Foundation 승인 입력 state:
  [`../archive/state/2026-08-27-phase3-foundation-acceptance-current-task.md`](../archive/state/2026-08-27-phase3-foundation-acceptance-current-task.md),
  [`../archive/state/2026-08-27-phase3-foundation-acceptance-implemented-state.md`](../archive/state/2026-08-27-phase3-foundation-acceptance-implemented-state.md)
- Foundation 승인까지의 worklog:
  [`../archive/worklogs/2026-08-27-through-phase3-foundation-acceptance.md`](../archive/worklogs/2026-08-27-through-phase3-foundation-acceptance.md)

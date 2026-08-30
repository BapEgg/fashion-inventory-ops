# Archived Implemented State — Phase 3 foundation acceptance input

Last updated: 2026-08-27

현재 저장소에서 관찰되는 동작, 검증 결과와 열린 결함만 유지하는 hot-state snapshot이다.

## Baseline and platform

- MVP-1 동결 기준선: [`../milestones/MVP-1.md`](../milestones/MVP-1.md)
- Java 21, Spring Boot 4.1.0, Gradle 9.5.1, Oracle Free 23ai/Flyway/JPA
- React 19, TypeScript 5.9, Vite 8
- MVP-1의 7일 Batch, 목록·상세·simulation·결정 REST/React와 AI-disabled 설명 경계는
  호환 기준선으로 유지된다. 실제 LLM provider 호출은 없다.

## MVP-2 data and deterministic rules — accepted

- immutable `V1`~`V13` Migration, `MVP-2-GS-V1` 합성 Seed와 validator가 존재한다.
- 28일 관측·신호·confidence·수요율, projection·exception·candidate·자동 scenario와 승인
  request 검증은 결정론적 Java가 담당한다.
- 수량·상태는 Java가 결정하고 AI는 설명만 담당한다. MVP-2 정책은 실제 기업 정책이 아닌
  데모 `ASSUMPTION`이다.

## Approval transaction and `MANUAL` preview — accepted

- 결정은 append-only이며 승인 transaction은 recommendation → donor snapshot 순서로 잠그고
  최신 version/route/snapshot/policy/inbound/open transfer/active draft를 재검증한다.
- 승인된 결정·근거·이동 초안은 한 transaction으로 저장되고 중간 실패는 rollback한다.
- `MANUAL`은 같은 current-basis와 eligibility 계산을 쓰는 side-effect-free preview다.
- REST/ProblemDetail/React wiring은 아직 구현하지 않았다.

## Phase 3 Batch foundation — findings fixed, Codex 재검증 대기

- `SpDemandEvent`, `SpMetricQualityFlag`, `SpCandidateReason`, `SpRebalanceScenario` entity와
  repository, 관련 enum이 V6 기존 테이블에 매핑됐다. 새 Migration은 없다.
- `SpInventoryMetric` MVP-2 생성자와 `SpRebalanceRecommendation.createMvp2Candidate(...)`,
  analysis-run triple lookup이 추가됐다. 기존 MVP-1 생성자는 유지된다.
- `RepresentativeEventSelection`은 관련 event를 `(startDate,eventCode)`로 정렬한다.
  Codex는 승인·`MANUAL`도 관측 구간+전체 plan horizon으로 대표 event를 먼저 고르고,
  이후 route window로 uplift 적용 여부를 판단한다는 Javadoc 문구를 직접 바로잡았다.

### Findings closed this round

1. **narrowing overflow**: `SpInventoryMetric.expectedShortageQuantity`가 `Integer`→`Long`로
   바뀌었고 생성자는 더 이상 `long`을 `int`로 cast하지 않는다.
2. **ELIGIBLE route 불변조건**: `createMvp2Candidate(...)`가 `ELIGIBLE`+null route를
   `IllegalArgumentException`으로 거부한다(`REJECTED/NONE`은 계속 nullable).
3. **scenario audit 파생**: `SpRebalanceScenario` 생성자에서 `expectedArrivalAt`/
   `candidateVersion` 인자를 제거하고 각각 `result.expectedArrivalDate()`+
   `00:00 Asia/Seoul`, `recommendation.getCandidateVersion()`에서 직접 파생한다.

정확한 수정·완료 조건은 [`current-task.md`](current-task.md)가 소유한다. Codex 재검증
전이므로 Phase 3 foundation은 아직 accepted가 아니다.

## Current verification evidence

- Codex 확장 표적(직전 라운드): `RepresentativeEventSelectionTest` 5/5,
  `DemandSignalClassificationTest` 9/9, `Mvp2BatchEntityPersistenceMappingIT` 7/7 — 21/21.
- Claude 표적(이번 라운드, 실제 실행): `SpRebalanceRecommendationTest` 3/3(신규, pure),
  `RepresentativeEventSelectionTest` 5/5, `DemandSignalClassificationTest` 9/9(둘 다 무수정),
  `Mvp2BatchEntityPersistenceMappingIT` 8/8(기존 7 + overflow 신규 1).
- Oracle 전체 Backend build: **305/305**, skip 0, failures/errors 0.
- DB-free 전체 build: **305 total / 246 passed / 59 Oracle-conditioned skip**,
  failures/errors 0. `git diff --check` 통과(기존 파일의 LF/CRLF 경고만).

## Not implemented

- Phase 3 Batch 입력 adapter, job/tasklet 계산·영속화 orchestration과 transaction/retry
- 승인·`MANUAL` event-aware effective BASE parity wiring
- `MVP-2-GS-V1` Batch golden scenario test
- MVP-2 계산/승인/MANUAL의 REST/React wiring과 실제 LLM provider adapter

## Cold evidence

- 리뷰 입력 state:
  [`../archive/state/2026-08-27-phase3-foundation-review-current-task.md`](../archive/state/2026-08-27-phase3-foundation-review-current-task.md),
  [`../archive/state/2026-08-27-phase3-foundation-review-implemented-state.md`](../archive/state/2026-08-27-phase3-foundation-review-implemented-state.md)
- 이번 리뷰까지의 worklog:
  [`../archive/worklogs/2026-08-27-through-phase3-foundation-review.md`](../archive/worklogs/2026-08-27-through-phase3-foundation-review.md)
- 이전 MANUAL 재리뷰 state/worklog link는 위 archived snapshot에서 이어진다.

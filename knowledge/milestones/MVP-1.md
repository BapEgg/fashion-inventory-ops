# MVP-1 Knowledge Checkpoint

Status: `FROZEN KNOWLEDGE BASELINE`
Cutoff: 2026-08-25
Checkpoint created: 2026-08-26

이 문서는 MVP-1에서 최종적으로 구현·검증된 상태와 MVP-2가 보존해야 할 경계를
고정한다. 세션별 수정 과정은 담지 않는다. 이 checkpoint는 지식 동결이며 별도
Git tag를 생성했다는 뜻은 아니다.

## Delivered scope

- Java 21, Spring Boot 4.1.0, Gradle, Oracle Database Free 23ai, Flyway
- React 19, TypeScript 5.9, Vite 8
- `V1`~`V5` domain schema, Spring Batch metadata, Comments와 `SYNTHETIC` demo data
- 7일 판매 기반 재고 지표와 점간 이동량의 결정론적 Java 계산
- Oracle-backed Batch 분석과 결과 persistence
- 분석 실행, 예외 목록·상세, 수량 시뮬레이션, 승인·거절 REST API
- 예외 목록과 상세·시뮬레이션·결정 React 화면
- AI-disabled 설명 endpoint와 provider 미구현 상태의 명시적 응답

## Durable behavior

- `InventoryMetricCalculation`이 availability, average sales, coverage,
  classification과 priority를 결정한다.
- `RebalanceCalculation`은 raw 7-day sales를 사용해 receiver target과 donor
  retained quantity를 계산한다. 곱셈·ceiling·safety-stock 합산은 widened/checked
  arithmetic을 사용하고 범위를 넘으면 조용히 wrap하지 않는다.
- Spring Batch `inventoryAnalysisJob`은 `(analysisDate, ruleVersion)`으로 멱등하다.
  `SimpleJobRepository` 기반 JDBC metadata와 domain `SpAnalysisRun` guard를 모두
  유지한다.
- 시뮬레이션은 source inventory와 결정을 변경하지 않는다.
- 결정은 비어 있지 않은 사유와 허용 수량을 요구하고 동일 recommendation의
  두 번째 terminal 결정을 거부한다.
- 목록·상세 resource는 actionable exception만 노출한다.
- Frontend는 Backend 결과를 표시하며 수량·상태 업무 규칙을 다시 계산하지 않는다.
- AI 경계는 read-only다. AI가 비활성·미설정이어도 핵심 흐름이 동작하며 AI는
  수량, 우선순위, 실행 가능 여부나 결정 상태를 만들지 않는다.

## Golden Scenario anchor

- 분석일: `2026-08-25`, rule version: `MVP-1`
- Gangnam receiver: available 5, `STOCKOUT_RISK/HIGH`
- Hongdae donor: available 40, `OVERSTOCK`
- 결정론적 recommendation quantity: 25
- 실제 재고 변경 없이 simulation과 terminal decision 흐름을 검증한다.

## Final verification evidence

- Oracle credentials를 사용한 Backend clean build: 27/27 tests passed,
  0 failures/errors, 6 suites.
- Oracle 없이 실행한 테스트에서 pure calculation, application context와
  explanation tests가 통과하고 Oracle IT는 정상 skip됐다.
- Frontend `tsc -b && vite build`가 통과했다.
- 실행 중인 Backend/Oracle을 대상으로 분석 재호출, 목록·상세·시뮬레이션과
  AI-disabled 설명 endpoint를 실제 호출했다.
- 실브라우저에서 목록과 상세 화면을 확인했고 console error가 없었다.
- Oracle readback에서 source inventory가 유지되고 Golden domain rows가
  `analysis_run=1`, `inventory_metric=3`, `recommendation=1`, `decision=1`로 유지됐다.
- JDBC JobRepository 구현 class와 실제 `BATCH_*` persistence를 회귀 테스트로
  검증하고 테스트 fixture 잔여가 없음을 확인했다.

## Compatibility boundary for MVP-2

- `V1`~`V5` Migration을 수정하지 않는다. 확장은 새 Migration으로 수행한다.
- MVP-1 endpoint와 `SP_REBALANCE_RECOMMENDATION`의 기존 의미를 호환 유지한다.
- 기존 `2026-08-25` Golden Scenario를 계산 회귀 기준으로 삭제하지 않는다.
- 재고 계산, 추천 수량과 승인 상태는 결정론적 Java가 소유한다.
- 실제 재고 차감, 외부 ERP 이동 실행과 LLM provider는 MVP-1에 포함되지 않는다.

## Deferred at checkpoint

- 실제 LLM provider adapter
- 인증, 외부 ERP/WMS/TMS 연동과 실제 물류 실행
- MVP-2의 28일 신호·confidence·복수 scenario·버전 입력과 Draft 흐름

MVP-2의 현재 진행 상태는
[`../state/implemented-state.md`](../state/implemented-state.md)에서만 갱신한다.

## Provenance

- 압축 전 전체 worklog:
  [`../archive/worklogs/2026-08-before-knowledge-compaction.md`](../archive/worklogs/2026-08-before-knowledge-compaction.md)
- 압축 전 구현 상태:
  [`../archive/state/2026-08-26-pre-compaction-implemented-state.md`](../archive/state/2026-08-26-pre-compaction-implemented-state.md)

원인 조사나 감사가 아닌 일반 Resume에서는 위 archive를 읽지 않는다.

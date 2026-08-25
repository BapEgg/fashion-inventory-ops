# Current Task

Status: MVP-2 Phase 1 Oracle 입력·Schema 완료; Phase 2 순수 Java 규칙 준비

Current role: Claude implementation
Last updated: 2026-08-25

## Goal

승인된 MVP-2 규칙을 Spring/JPA와 독립된 결정론적 Java 값 객체와 계산으로
구현하고 GS-01~GS-06 단위 테스트로 고정한다. Phase 1 Schema와 Seed를 다시
설계하거나 MVP-1 동작을 변경하지 않는다.

## Implemented prerequisite

- `V6__evolve_stockpilot_mvp2_schema.sql`: legacy backfill, 입력·결과·결정·Draft
  Schema와 호환성 제약
- `V7__load_mvp2_synthetic_scenarios.sql`: `MVP-2-GS-V1` GS-01~GS-06 입력
- `V8__add_mvp2_domain_comments.sql`: 허용값과 데모 경계
- `data/seed/mvp2`와 확장된 `scripts/validate-seed.ps1`
- 깨끗한 Oracle 및 기존 V5→V8 업그레이드, 백엔드 27개 테스트, Seed 검증,
  Frontend build 통과

자세한 사실과 checksum은 `knowledge/state/implemented-state.md`의
MVP-2 transition boundary를 따른다.

## Required specification

Phase 2에 필요한 부분만 읽는다.

1. `knowledge/business-rules.md` 1~12절
2. `knowledge/data-model.md` 5~7절과 V6 실제 Schema
3. `knowledge/project.md` Golden Scenario와 구현 순서
4. 기존 순수 Java `InventoryMetricCalculation`, `RebalanceCalculation`과 테스트
5. `data/seed/mvp2/README.md`

문서와 Schema가 다르면 실제 V6~V8과 Oracle 적용 결과를 우선하고 차이를 보고한다.

## Confirmed MVP-2 demo boundary

1. 28일, 최소 14일, CV 0.35, 급증, 24시간, 7일/14일과 fallback은
   rule version `MVP-2`의 `ASSUMPTION`이다.
2. 같은 소유권 또는 명시적으로 허용된 국내 매장 사이만 이동 가능하다.
3. 실제 배송 과정 없이 방향성 경로의 lead time만 계산한다.
4. 이벤트 uplift는 low/base/high 입력이며 Java나 AI가 예측하지 않는다.
5. `VARIABLE`은 비교 시나리오만 만들고 기본 추천 수량은 `NULL`이다.
6. 승인 검증은 수량·버전·상태를 결정하지만 재고를 변경하지 않는다.
7. AI는 Java 결과를 설명할 뿐 신호·수량·후보·상태를 결정하지 않는다.

## Phase 2 implementation order

1. 28일 관측 입력과 OOS 검열, 최신성, 이벤트·거래 특성을 표현하는 immutable
   Java input/value object를 만든다.
2. `effectiveDemandDays`, 품질 플래그와 관측 통계를 scale/rounding 규칙까지
   순수 함수로 구현한다.
3. 수요 신호 우선순위, confidence, low/base/high 수요율을 구현한다.
4. 입고·진행 중 이동을 반영한 projected available과 exception/severity를 구현한다.
5. 소유권·국내 경로·lead time·최소/배수/최대·용량·donor 보존 규칙으로 후보와
   모든 탈락 사유를 결정한다.
6. `NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE` 양쪽 매장 결과를 계산한다.
   `VARIABLE`에는 대표 추천 수량을 만들지 않는다.
7. 승인 요청의 stale version, 허용 수량, 상태 shape를 순수 Java로 검증하되
   Draft 저장·DB 잠금·REST 처리는 Phase 4로 남긴다.
8. GS-01~GS-06과 경계값·overflow·rounding 단위 테스트를 추가하고 MVP-1 전체
   회귀 테스트를 실행한다.

## Constraints

- V1~V8 Migration과 Seed를 수정하지 않는다. 필요한 Schema 결함을 발견하면
  새 Migration 근거 없이는 진행하지 않고 보고한다.
- Phase 2에서는 JPA entity, Batch, REST, React, AI provider를 구현하지 않는다.
- 임계값과 상태 결정은 Java 코드 한 곳에서 관리하고 AI prompt에 복제하지 않는다.
- `SP_REBALANCE_RECOMMENDATION` 호환 의미와 Scenario 자식 구조를 유지한다.
- 실제 기업 정책, 운영 데이터, 산업 표준이라고 표현하지 않는다.

## Definition of done

- GS-01~GS-06이 승인된 신호·품질·후보 분기로 결정론적으로 재현된다.
- low/base/high, projected inventory, 후보 사유와 네 시나리오가 문서 식과
  scale/rounding에 맞는 exact assertion으로 고정된다.
- `VARIABLE` 대표 추천량 부재, owner mismatch, long lead time, confirmed inbound
  분기가 명시적으로 테스트된다.
- 모든 신규 테스트와 기존 MVP-1 테스트의 실제 실행 결과만 상태 문서에 기록한다.

## Blockers

현재 알려진 blocker는 없다. 규칙 문서가 하나의 입력에서 서로 다른 결과를
요구하거나 Phase 2에 Public API/DB 변경이 필요하면 임의로 확장하지 않고 보고한다.

## Next verifiable action

기존 두 순수 Java 계산 클래스와 테스트 구조를 읽고, MVP-2 입력·출력 value
object와 GS-01~GS-06 테스트 이름을 먼저 고정한다.

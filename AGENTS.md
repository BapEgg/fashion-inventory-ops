# StockPilot Agent Guide

## 작업 순서

1. 작업 시작 시 `.agents/skills/stockpilot-resume/SKILL.md`를 사용한다.
2. `knowledge/state/current-task.md`에서 현재 역할과 목표를 확인한다.
3. 현재 작업에 필요한 문서만 선택적으로 읽는다.
4. 의미 있는 작업 종료 또는 역할 인계 시 `.agents/skills/stockpilot-worklog/SKILL.md`를 사용한다.
5. MVP/Phase 완료 또는 hot-state 크기 초과 시 `.agents/skills/stockpilot-checkpoint/SKILL.md`로 원본을 보존하고 상태를 압축한다.

전체 `knowledge` 폴더를 매번 읽지 않는다.

## 역할

- Codex 기획·설계: 요구사항, 업무 규칙, 완료 조건과 기술 결정을 명확히 한다.
- Claude 구현: 확정된 명세 범위만 구현하고 실제 테스트·빌드 결과를 기록한다.
- Codex 검증·리뷰: 리뷰를 먼저 제공하며 사소한 결함만 직접 수정한다. 범위나 정책이 바뀌는 수정은 사용자에게 확인한다.

## 공통 규칙

- 특정 기업의 내부 데이터나 정책을 안다고 가정하지 않는다.
- `ASSUMPTION`은 데모 가정이며 실제 기업 정책이 아님을 문서와 화면에 명시한다.
- 재고 계산, 추천 수량과 승인 상태는 결정론적인 Java 코드가 담당한다.
- AI는 Java 결과의 설명만 담당하며 수량이나 상태를 결정하지 않는다.
- 비밀번호와 API Key를 Commit하지 않는다.
- 실행하지 않은 테스트를 성공했다고 기록하지 않는다.
- 기능별 빈 폴더나 미래용 문서를 미리 만들지 않는다.
- 공식 문서와 코드가 충돌하면 실제 코드, DB Migration, 설정과 테스트 결과를 우선한다.
- `current-task.md`와 `implemented-state.md`에는 현재 사실만 유지한다. 완료된 수정·리뷰 과정은 Worklog에만 기록하고 state에 append하지 않는다.
- `knowledge/archive`는 회귀 원인 조사나 감사가 필요할 때만 읽으며 기본 Resume 대상에 포함하지 않는다.

## 변경 중단 조건

다음 상황에서는 임의로 진행하지 않고 현재 상태와 선택지를 보고한다.

- 확정된 업무 규칙과 구현이 충돌한다.
- Public API 또는 DB Schema 변경이 필요하지만 명세에 근거가 없다.
- AI와 Java의 책임 경계를 변경해야 한다.
- 합성 데이터와 실제 데이터의 구분이 불명확하다.
- 사용자 선택에 따라 결과가 크게 달라지는 외부 서비스나 인프라 결정이 필요하다.

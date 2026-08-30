# StockPilot Knowledge Start

이 폴더는 설계, 현재 작업과 구현 상태를 이어받기 위한 Source of Truth다.
사용자를 위한 첫 설명은 루트 [`README.md`](../README.md)다.

## 최소 읽기 순서

1. [`state/current-task.md`](state/current-task.md): 현재 역할, 열린 문제, 다음 검증
2. [`state/implemented-state.md`](state/implemented-state.md): 현재 실제 구현 스냅샷
3. 현재 작업에 필요한 공식 문서 하나
   - 범위·완료 조건: [`project.md`](project.md)
   - 계산·데이터·AI 경계: [`business-rules.md`](business-rules.md)
   - ERD·Oracle Schema·적재: [`data-model.md`](data-model.md)
4. 상태 문서가 참조할 때만 관련 milestone checkpoint

현재 문서로 사실이 부족할 때만 active worklog의 최신 관련 항목을 읽는다.
`archive`는 회귀 원인 조사나 감사가 명시적으로 필요할 때만 검색한다. 모든
knowledge 문서나 과거 로그를 매번 읽지 않는다.

## 문서 책임

- `project.md`: 제품 정의, 범위, 아키텍처와 완료 조건. 진행 일지는 두지 않는다.
- `business-rules.md`: 구현과 테스트가 따라야 할 계산 명세와 데모 가정값
- `data-model.md`: ERD, 무결성 규칙과 합성 데이터 적재 절차
- `state/current-task.md`: 지금 해야 할 일만 담는 교체형 hot snapshot
- `state/implemented-state.md`: 현재 존재하는 기능·검증·미구현만 담는 hot snapshot
- `milestones`: 완료된 MVP/Phase의 최종 범위와 지속될 불변조건
- `worklogs`: 활성 기간의 짧은 append-only 변경·검증·미해결 기록
- `archive`: 압축 전 원본 기록. 보존하지만 기본 읽기 대상이 아닌 cold history

같은 사실을 state와 worklog에 서술형으로 복제하지 않는다. State에는 최종 결과만,
worklog에는 어떻게 그 결과에 도달했는지만 기록한다.

## Hot-state budget

- `current-task.md`: 권장 최대 120줄 또는 12KB
- `implemented-state.md`: 권장 최대 250줄 또는 30KB
- active monthly worklog: 권장 최대 50KB 또는 30개 entry

MVP/Phase가 끝나거나 위 기준을 넘으면
[`stockpilot-checkpoint`](../.agents/skills/stockpilot-checkpoint/SKILL.md)로 원본을
archive하고 milestone/state를 다시 쓴다. 압축은 기록 삭제가 아니라 기본 읽기
경로에서 cold archive로 이동하는 작업이다.

## 우선순위

문서와 구현이 충돌하면 실제 코드, DB Migration, 설정과 실행한 테스트 결과를
우선 확인하고 hot state와 checkpoint를 바로잡는다. 실행하지 않은 테스트를
검증 사실로 기록하지 않는다.

# StockPilot Knowledge Start

이 폴더는 에이전트가 설계와 구현 상태를 이어받기 위한 Source of Truth입니다. 사용자를 위한 첫 설명은 루트 [`README.md`](../README.md)입니다.

## 최소 읽기 순서

1. [`state/current-task.md`](state/current-task.md): 지금 역할, 목표, 다음 작업
2. [`state/implemented-state.md`](state/implemented-state.md): 실제로 존재하고 검증된 것
3. 현재 작업에 필요한 문서 하나만 추가로 읽기
   - 범위·완료 조건: [`project.md`](project.md)
   - 계산·데이터·AI 경계: [`business-rules.md`](business-rules.md)
   - ERD·Oracle Schema·데이터 적재: [`data-model.md`](data-model.md)

`worklogs`는 현재 문서만으로 맥락이 부족할 때 최신 항목 하나만 확인합니다. 모든 문서를 매번 읽지 않습니다.

## 문서 책임

- `project.md`: 바뀌기 어려운 제품 정의, 범위, 아키텍처와 완료 조건
- `business-rules.md`: 구현과 테스트가 따라야 할 계산 명세와 데모 가정값
- `data-model.md`: ERD, 무결성 규칙과 합성 데이터 적재 절차
- `state/current-task.md`: 바로 다음 세션이 해야 할 일
- `state/implemented-state.md`: 문서가 아닌 실제 코드·빌드·테스트 상태
- `worklogs`: 역할 사이에서 필요한 짧은 변경·검증·미해결 기록

## 우선순위

문서와 구현이 충돌하면 실제 코드, DB Migration, 설정과 실행한 테스트 결과를 우선 확인하고 문서를 바로잡습니다.

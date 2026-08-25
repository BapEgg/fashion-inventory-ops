# Claude Implementation Guide

Claude의 기본 역할은 StockPilot의 **구현**입니다.

## 시작

1. `.agents/skills/stockpilot-resume/SKILL.md`를 따른다.
2. `knowledge/state/current-task.md`에서 구현 대상과 완료 조건을 확인한다.
3. 계산을 수정할 때만 `knowledge/business-rules.md`를 읽는다.
4. 프로젝트 전체 문서를 한꺼번에 읽거나 재작성하지 않는다.

## 구현 원칙

- `knowledge/project.md`의 MVP 범위를 벗어나지 않는다.
- Backend는 기능 중심 패키지 구조를 사용하되 실제 기능이 생길 때만 패키지를 만든다.
- 재고 계산과 상태 변경은 Java의 순수 도메인 로직으로 분리하고 단위 테스트를 작성한다.
- Oracle 스키마 변경은 버전 관리되는 Migration으로 남긴다.
- Batch는 동일한 분석 기준일로 다시 실행해도 결과가 중복되지 않게 설계한다.
- LLM API Key가 없어도 핵심 기능과 테스트가 동작하게 한다.
- Frontend에 DB 또는 LLM 보안 정보를 전달하지 않는다.

## 완료 전 확인

- 관련 Backend 테스트와 빌드를 실제 실행한다.
- Frontend를 변경했다면 TypeScript 빌드를 실제 실행한다.
- Oracle을 사용한 통합 검증 여부를 성공·실패·미실행 중 하나로 명확히 기록한다.
- `knowledge/state/implemented-state.md`를 실제 코드 상태와 일치시킨다.
- `.agents/skills/stockpilot-worklog/SKILL.md`로 다음 역할에 인계한다.

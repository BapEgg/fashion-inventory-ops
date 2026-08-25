# Synthetic Seed Data

StockPilot의 대표 시나리오를 재현하는 버전 관리 데이터입니다.

## 분류

- 데이터 유형: `SYNTHETIC`
- 기준일: `2026-08-25`
- 판매 관찰 기간: `2026-08-18` ~ `2026-08-24`
- 목적: 하나의 SKU와 세 개 매장으로 품절 위험·과잉재고·정상 상태를 모두 재현

실제 기업의 상품, 매장, 재고 또는 판매 데이터가 아닙니다.

## 파일

- `products.csv`: 분석 대상 SKU
- `stores.csv`: 합성 매장
- `inventory.csv`: 기준일의 보유·예약 재고
- `sales.csv`: 최근 7일의 일별 판매 수량

## 기대 시나리오

- `STORE-GANGNAM`은 판매 속도 대비 재고가 적어 품절 위험으로 탐지됩니다.
- `STORE-HONGDAE`는 판매 속도 대비 재고가 많아 과잉재고로 탐지됩니다.
- `STORE-SEONGSU`는 비교를 위한 정상 범위 매장입니다.
- 재배분 추천은 `STORE-HONGDAE`에서 `STORE-GANGNAM` 방향으로 생성되어야 합니다.

정확한 계산식과 임계값은 [`knowledge/business-rules.md`](../../knowledge/business-rules.md)를 따릅니다.

## 검증

```powershell
.\scripts\local.ps1 seed-check
```

CSV 수집·검증·Oracle 적재 절차와 ERD는 [`knowledge/data-model.md`](../../knowledge/data-model.md)를 따릅니다.

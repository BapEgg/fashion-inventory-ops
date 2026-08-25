# MVP-2 Synthetic Input Contract

이 디렉터리는 입력 버전 `MVP-2-GS-V1`의 GS-01~GS-06을 재현합니다.
모든 데이터는 `SYNTHETIC`이며, 임계값·커버리지·경로·정책 값은 데모
`ASSUMPTION`입니다. 실제 F&F 데이터나 정책 또는 검증된 산업 표준이 아닙니다.

## 기준

- 분석일: `2026-09-30`
- 판매 관측 구간: `2026-09-02`~`2026-09-29` 28일
- 재고: 관측 구간 28일과 분석일 최신 스냅샷을 합쳐 매장–SKU당 29행
- 범위: `DOMESTIC` 매장. 같은 소유권 또는 명시적 owner override만 이동 가능
- 경로: 방향별 `lead_time_days`만 입력하며 실제 배송 단계는 구현하지 않음
- 이벤트 uplift: 시스템 예측값이 아니라 `low/base/high` 입력값

## 파일과 행 수

| 파일 | Oracle 대상 | 행 수 |
|---|---|---:|
| `products.csv` | `SP_PRODUCT` | 6 |
| `stores.csv` | `SP_STORE` | 3 |
| `inventory-daily.csv` | `SP_INVENTORY_SNAPSHOT` | 348 |
| `sales-daily.csv` | `SP_DAILY_SALE` | 336 |
| `demand-events.csv` | `SP_DEMAND_EVENT` | 1 |
| `inbound-schedules.csv` | `SP_INBOUND_SCHEDULE` | 1 |
| `open-transfers.csv` | `SP_OPEN_TRANSFER` | 1 |
| `transfer-routes.csv` | `SP_STORE_TRANSFER_ROUTE` | 2 |
| `store-sku-policies.csv` | `SP_STORE_SKU_POLICY` | 12 |

## Golden Scenarios

| ID | 입력 특성 | 후속 계산에서 재현할 분기 |
|---|---|---|
| GS-01 | 수요 매장 28일 매일 2개 판매 | 안정 반복 수요 |
| GS-02 | 미래 프로모션과 uplift `1.20/1.50/1.80` | 알려진 이벤트 |
| GS-03 | 한 거래에서 20개, 나머지 27일 0개 | 설명되지 않은 급증 |
| GS-04 | 처음 14일 품절·판매 0, 이후 14일 관측 가능 | OOS 검열 |
| GS-05 | 수요 매장에 50개 확정 입고 | 입고가 부족을 이미 해소 |
| GS-06 | 국내 매장이지만 소유권 불일치, override 없음, 리드타임 10일 | 후보 탈락 사유 |

이 단계는 입력과 Oracle Schema만 구현합니다. 수요 신호·시나리오 수량·후보 상태는
후속 결정론적 Java 구현이 계산하며, AI는 그 결과를 결정하지 않습니다.
`VARIABLE`은 비교 결과만 저장할 수 있고 기본 추천 수량은 두지 않습니다.
승인은 재고를 바꾸지 않고 `SP_TRANSFER_DRAFT`만 생성합니다.

## 검증

```powershell
.\scripts\local.ps1 seed-check
```

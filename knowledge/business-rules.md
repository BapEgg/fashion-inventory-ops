# Inventory Analysis Business Rules

Status: Approved demo assumptions for MVP<br>
Rule version: `MVP-1`<br>
Analysis date in Seed: `2026-08-25`

> All thresholds and targets in this document are `ASSUMPTION` values created for the demo. They are not actual company policy.

## 1. Terms

- `onHandQuantity`: physical quantity recorded at the analysis snapshot
- `reservedQuantity`: quantity already reserved and unavailable for rebalancing
- `availableQuantity`: quantity that can be considered by the analysis
- `averageDailySales`: mean daily sales during the observation window
- `coverageDays`: estimated days the available quantity can support at the observed sales rate
- `receiver`: store that needs inventory
- `donor`: store that may release inventory

Quantities are whole, non-negative units. A validation error is raised when a source quantity is negative or reserved quantity exceeds on-hand quantity.

## 2. Calculation Rules

### Available quantity

```text
availableQuantity = onHandQuantity - reservedQuantity
```

Planned inbound quantity is excluded from MVP calculations because no inbound schedule is in the Seed contract.

### Average daily sales

Observation window: previous seven calendar days, excluding the analysis date.

```text
averageDailySales = sum(soldQuantity in 7-day window) / 7
```

Intermediate and API display values use a scale of two decimals and `HALF_UP` rounding. Transfer calculations use the unrounded value.

### Coverage days

```text
coverageDays = availableQuantity / averageDailySales
```

- If `averageDailySales > 0`, calculate normally.
- If `averageDailySales = 0` and available quantity is positive, coverage is conceptually unlimited and the record is an overstock candidate.
- If both values are zero, coverage is undefined and the record is not an actionable exception in MVP.

## 3. Classification Assumptions

| Rule | ASSUMPTION value |
|---|---:|
| Stockout-risk threshold | `coverageDays <= 3` |
| Overstock threshold | `coverageDays >= 21` |
| Receiver target coverage | `7 days` |
| Donor retained coverage | `14 days` |
| Safety stock | `2 units` |

Classification order prevents overlap:

1. undefined/non-actionable
2. stockout risk
3. overstock
4. normal

## 4. Rebalancing Rules

A donor and receiver must have the same `skuId`, be different stores, and belong to the same analysis result.

```text
receiverTargetQuantity = ceil(receiverAverageDailySales * 7) + 2
receiverShortage = max(receiverTargetQuantity - receiverAvailableQuantity, 0)

donorRetainedQuantity = ceil(donorAverageDailySales * 14) + 2
donorTransferableQuantity = max(donorAvailableQuantity - donorRetainedQuantity, 0)

recommendedTransferQuantity = min(receiverShortage, donorTransferableQuantity)
```

No recommendation is created when the result is zero.

### Simulation

The requested transfer quantity must be an integer in the range:

```text
1 <= requestedQuantity <= donorTransferableQuantity
```

Simulation calculates both stores' available quantity and coverage after transfer. It does not persist inventory movement.

## 5. Priority

MVP supports two levels:

- `CRITICAL`: stockout risk with `coverageDays <= 1`
- `HIGH`: other actionable stockout risks

Donor overstock records are evidence for a recommendation and do not need a separate user-facing priority in the first screen.

## 6. Decision State

- Initial recommendation status: `PENDING`
- Allowed terminal states: `APPROVED`, `REJECTED`
- Approval and rejection require a non-blank reason.
- A terminal decision cannot be changed in MVP.
- The selected quantity must have a valid simulation before approval.

## 7. AI Boundary

The AI input may contain only already-calculated facts and allowed labels. The AI may explain:

- why the record was classified as an exception
- how the recommended quantity was derived
- how coverage changes after simulation

The AI must not calculate a new quantity, alter a status, write directly to Oracle or claim that an assumption is actual company policy.

When AI is disabled or unconfigured, the API returns an explicit unavailable state while all deterministic functions remain usable.

## 8. Golden Scenario Expected Values

| Store | Available | 7-day sales | Avg/day | Coverage | Classification |
|---|---:|---:|---:|---:|---|
| Gangnam | 5 | 28 | 4.00 | 1.25 | Stockout risk / HIGH |
| Hongdae | 40 | 4 | 0.57 | 70.00 | Overstock |
| Seongsu | 11 | 9 | 1.29 | 8.56 | Normal |

For Hongdae → Gangnam:

- receiver target: `ceil(4.00 × 7) + 2 = 30`
- receiver shortage: `30 - 5 = 25`
- donor retained: `ceil((4 / 7) × 14) + 2 = 10`
- donor transferable: `40 - 10 = 30`
- expected recommendation: `min(25, 30) = 25 units`

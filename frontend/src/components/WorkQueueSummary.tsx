import type { AllocatorWorkSummary } from '../types'
import { formatMoney, formatQuantity } from '../formatters'

/**
 * The summary tiles, per spec section 7.1 as extended by 2026-08-30 allocator feedback: the
 * numbers reflect every currently-applied filter except workStatus (so they always match what a
 * "전체" tab click would show), not a frozen run-wide total. A tile click still resets every
 * other filter and applies only that tile's own workStatus/severity/sort, then returns to page 0
 * -- only the displayed count became filter-aware, not the click behavior.
 */
export function WorkQueueSummary({
  summary,
  onSelectAll,
  onSelectCritical,
  onSelectDecisionRequired,
  onSelectReviewInput,
  onSelectBySalesExposure,
}: {
  summary: AllocatorWorkSummary
  onSelectAll: () => void
  onSelectCritical: () => void
  onSelectDecisionRequired: () => void
  onSelectReviewInput: () => void
  onSelectBySalesExposure: () => void
}) {
  return (
    <section className="work-queue-summary" aria-label="오늘의 처리 현황">
      <button type="button" className="work-queue-summary__tile" onClick={onSelectAll}>
        <span className="work-queue-summary__value">{formatQuantity(summary.totalReviewTargets)}</span>
        <span className="work-queue-summary__label">전체 처리 대상</span>
      </button>
      <button type="button" className="work-queue-summary__tile work-queue-summary__tile--critical" onClick={onSelectCritical}>
        <span className="work-queue-summary__value">{formatQuantity(summary.criticalCount)}</span>
        <span className="work-queue-summary__label">긴급</span>
      </button>
      <button type="button" className="work-queue-summary__tile" onClick={onSelectDecisionRequired}>
        <span className="work-queue-summary__value">{formatQuantity(summary.decisionRequiredCount)}</span>
        <span className="work-queue-summary__label">이동 결정 필요</span>
      </button>
      <button type="button" className="work-queue-summary__tile" onClick={onSelectReviewInput}>
        <span className="work-queue-summary__value">{formatQuantity(summary.reviewInputCount)}</span>
        <span className="work-queue-summary__label">원인·데이터 확인</span>
      </button>
      <button type="button" className="work-queue-summary__tile work-queue-summary__tile--wide" onClick={onSelectBySalesExposure}>
        <span className="work-queue-summary__value">{formatMoney(summary.estimatedSalesExposureTotal)}</span>
        <span className="work-queue-summary__label">
          매출 노출액(참고)
          {summary.estimatedSalesExposureUnknownCount > 0 && ` · 금액 미산정 ${summary.estimatedSalesExposureUnknownCount}건`}
        </span>
      </button>
    </section>
  )
}

import type { AllocatorWorkStatus, AllocatorWorkSummary } from '../types'

/** `null` means the 전체 tab -- no `workStatus` filter applied at all. */
export type WorkStatusTabValue = AllocatorWorkStatus | null

const TABS: { value: WorkStatusTabValue; label: string; count: (s: AllocatorWorkSummary) => number }[] = [
  { value: null, label: '전체', count: (s) => s.totalReviewTargets },
  { value: 'DECISION_REQUIRED', label: '이동 결정 필요', count: (s) => s.decisionRequiredCount },
  { value: 'ON_HOLD', label: '확인 후 재검토', count: (s) => s.onHoldCount },
  { value: 'REVIEW_INPUT', label: '원인·데이터 확인', count: (s) => s.reviewInputCount },
  { value: 'NO_TRANSFER_OPTION', label: '이동안 없음', count: (s) => s.noTransferOptionCount },
  { value: 'COMPLETED', label: '처리 완료', count: (s) => s.completedCount },
]

/**
 * The processing-state tab strip, per spec section 7.2 -- changes only the `workStatus` filter,
 * leaving every other applied filter untouched. Default selection (전체, so a refresh never lands
 * the allocator on a partially-filtered view) is decided by the caller, not this component.
 */
export function WorkStatusTabs({
  summary,
  active,
  onSelect,
}: {
  summary: AllocatorWorkSummary
  active: WorkStatusTabValue
  onSelect: (value: WorkStatusTabValue) => void
}) {
  return (
    <nav className="work-status-tabs" aria-label="처리 상태">
      {TABS.map((tab) => (
        <button
          key={tab.value ?? 'ALL'}
          type="button"
          className={`work-status-tabs__tab${active === tab.value ? ' work-status-tabs__tab--active' : ''}`}
          aria-current={active === tab.value ? 'true' : undefined}
          onClick={() => onSelect(tab.value)}
        >
          {tab.label} <span className="work-status-tabs__count">{tab.count(summary)}</span>
        </button>
      ))}
    </nav>
  )
}

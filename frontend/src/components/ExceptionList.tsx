import type { Mvp2InventoryExceptionListItem, Mvp2InventoryExceptionPage } from '../types'
import { demandConfidenceLabel, demandSignalLabel, exceptionTypeLabel, qualityFlagLabel, severityLabel } from '../labels'
import { formatCoverageDays, formatDateTime, formatMoney, formatOrDash, formatQuantity } from '../formatters'

/**
 * Backend's fixed sort is rendered as-is -- no client-side re-sort -- and rows with
 * `hasExecutableCandidate=false` are never hidden, per the React wiring spec section 4.2. Each
 * row exposes exactly one focusable button for detail entry; no click handler is attached to the
 * row itself.
 */
export function ExceptionList({
  page,
  onSelectMetric,
  onPageChange,
  onResetFilters,
}: {
  page: Mvp2InventoryExceptionPage
  onSelectMetric: (inventoryMetricId: number) => void
  onPageChange: (nextPage: number) => void
  onResetFilters: () => void
}) {
  if (page.items.length === 0) {
    return (
      <div className="exception-list__empty">
        <p>조건에 맞는 재고 예외가 없습니다.</p>
        <button type="button" onClick={onResetFilters}>
          필터 초기화
        </button>
      </div>
    )
  }

  return (
    <div className="exception-list">
      <div className="exception-list__scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">심각도 / 예외 유형</th>
              <th scope="col">매장</th>
              <th scope="col">상품</th>
              <th scope="col">현재 가용재고</th>
              <th scope="col">재고 보유일수</th>
              <th scope="col">예상 부족수량</th>
              <th scope="col">가장 빠른 확정 입고</th>
              <th scope="col">수요 신호 / 신뢰도 / 품질</th>
              <th scope="col">후보 수(실행/비교/탈락)</th>
              <th scope="col">예상 매출 영향</th>
              <th scope="col">상세</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map((item) => (
              <ExceptionRow key={item.inventoryMetricId} item={item} onSelectMetric={onSelectMetric} />
            ))}
          </tbody>
        </table>
      </div>

      <nav className="exception-list__pagination" aria-label="페이지 이동">
        <button type="button" disabled={!page.hasPrevious} onClick={() => onPageChange(page.page - 1)}>
          이전
        </button>
        <span>
          {page.page + 1} / {Math.max(page.totalPages, 1)} 페이지 · 총 {formatQuantity(page.totalElements)}건
        </span>
        <button type="button" disabled={!page.hasNext} onClick={() => onPageChange(page.page + 1)}>
          다음
        </button>
      </nav>
    </div>
  )
}

function ExceptionRow({
  item,
  onSelectMetric,
}: {
  item: Mvp2InventoryExceptionListItem
  onSelectMetric: (inventoryMetricId: number) => void
}) {
  return (
    <tr>
      <td>
        {severityLabel(item.severity)} / {exceptionTypeLabel(item.inventoryExceptionType)}
      </td>
      <td>
        <div>{item.storeName ?? formatOrDash(item.storeId)}</div>
        <div className="exception-list__secondary">{item.region ?? formatOrDash(item.storeId)}</div>
      </td>
      <td>
        <div>{item.productName ?? formatOrDash(item.skuId)}</div>
        <div className="exception-list__secondary">
          {[item.color, item.sizeName].filter(Boolean).join(' / ') || '—'} · {item.skuId}
        </div>
      </td>
      <td>{formatQuantity(item.availableQuantity)}</td>
      <td>{formatCoverageDays(item.coverageDays)}</td>
      <td>{formatQuantity(item.expectedShortageQuantity)}</td>
      <td>
        {formatDateTime(item.nextConfirmedInboundAt)}
        {item.upcomingConfirmedInboundQuantity !== null && (
          <span className="exception-list__secondary"> ({formatQuantity(item.upcomingConfirmedInboundQuantity)})</span>
        )}
      </td>
      <td>
        <div>{demandSignalLabel(item.primaryDemandSignalType)}</div>
        <div className="exception-list__secondary">
          {demandConfidenceLabel(item.demandConfidence)}
          {item.qualityFlags.length > 0 && ` · ${item.qualityFlags.map(qualityFlagLabel).join(', ')}`}
        </div>
      </td>
      <td>
        {item.executableCandidateCount} / {item.comparisonOnlyCandidateCount} / {item.rejectedCandidateCount}
      </td>
      <td>{formatMoney(item.estimatedSalesImpact)}</td>
      <td>
        {item.inventoryMetricId !== null && (
          <button type="button" onClick={() => onSelectMetric(item.inventoryMetricId as number)}>
            상세 보기
          </button>
        )}
      </td>
    </tr>
  )
}

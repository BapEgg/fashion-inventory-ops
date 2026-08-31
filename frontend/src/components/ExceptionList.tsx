import type { ExceptionSortKey, Mvp2InventoryExceptionListItem, Mvp2InventoryExceptionPage, SortDirection } from '../types'
import {
  demandConfidenceLabel,
  demandSignalLabel,
  qualityFlagLabel,
  rejectionReasonLabel,
  severityLabel,
  workStatusLabel,
} from '../labels'
import { formatCoverageDays, formatDateTime, formatMoney, formatOrDash, formatQuantity } from '../formatters'

interface SortOption {
  value: string
  sortBy: ExceptionSortKey
  sortDirection: SortDirection
  label: string
}

const SORT_OPTIONS: SortOption[] = [
  { value: 'WORK_PRIORITY', sortBy: 'WORK_PRIORITY', sortDirection: 'ASC', label: '업무 우선순위' },
  { value: 'SALES_EXPOSURE', sortBy: 'SALES_EXPOSURE', sortDirection: 'DESC', label: '매출 노출액 높은 순' },
  { value: 'SHORTAGE_QUANTITY', sortBy: 'SHORTAGE_QUANTITY', sortDirection: 'DESC', label: '목표재고 부족 큰 순' },
  { value: 'COVERAGE_DAYS', sortBy: 'COVERAGE_DAYS', sortDirection: 'ASC', label: '재고일수 낮은 순' },
  { value: 'STORE_PRODUCT', sortBy: 'STORE_PRODUCT', sortDirection: 'ASC', label: '매장·상품 순' },
]

const HEADER_SORT_DEFAULTS: Record<'SHORTAGE_QUANTITY' | 'COVERAGE_DAYS' | 'SALES_EXPOSURE', SortDirection> = {
  SHORTAGE_QUANTITY: 'DESC',
  COVERAGE_DAYS: 'ASC',
  SALES_EXPOSURE: 'DESC',
}

/**
 * 처리 대상 worklist, per redesign spec section 7.5-7.6. Backend가 계산한 order를 그대로
 * 렌더링한다 -- client-side 재정렬은 절대 하지 않는다. 각 행은 명시적 버튼 하나로만 상세에
 * 진입하고, 행 전체에는 click handler를 붙이지 않는다.
 */
export function ExceptionList({
  page,
  sortBy,
  sortDirection,
  onSortChange,
  onSelectMetric,
  onPageChange,
  onResetFilters,
}: {
  page: Mvp2InventoryExceptionPage
  sortBy: ExceptionSortKey
  sortDirection: SortDirection | null
  onSortChange: (sortBy: ExceptionSortKey, sortDirection: SortDirection) => void
  onSelectMetric: (inventoryMetricId: number) => void
  onPageChange: (nextPage: number) => void
  onResetFilters: () => void
}) {
  const effectiveDirection = sortDirection ?? SORT_OPTIONS.find((o) => o.sortBy === sortBy)?.sortDirection ?? 'ASC'
  const selectValue = SORT_OPTIONS.find((o) => o.sortBy === sortBy && o.sortDirection === effectiveDirection)?.value ?? sortBy

  function handleSelectChange(value: string) {
    const option = SORT_OPTIONS.find((o) => o.value === value)
    if (option) {
      onSortChange(option.sortBy, option.sortDirection)
    }
  }

  function toggleHeaderSort(key: 'SHORTAGE_QUANTITY' | 'COVERAGE_DAYS' | 'SALES_EXPOSURE') {
    if (sortBy === key) {
      onSortChange(key, effectiveDirection === 'ASC' ? 'DESC' : 'ASC')
    } else {
      onSortChange(key, HEADER_SORT_DEFAULTS[key])
    }
  }

  function ariaSortFor(key: 'SHORTAGE_QUANTITY' | 'COVERAGE_DAYS' | 'SALES_EXPOSURE'): 'ascending' | 'descending' | 'none' {
    if (sortBy !== key) return 'none'
    return effectiveDirection === 'ASC' ? 'ascending' : 'descending'
  }

  return (
    <div className="exception-list">
      <div className="exception-list__sort-bar">
        <label>
          정렬
          <select value={selectValue} onChange={(e) => handleSelectChange(e.target.value)}>
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {page.items.length === 0 ? (
        <div className="exception-list__empty">
          <p>조건에 맞는 처리 대상이 없습니다.</p>
          <button type="button" onClick={onResetFilters}>
            필터 초기화
          </button>
        </div>
      ) : (
        <div className="exception-list__scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">처리 상태</th>
                <th scope="col">업무 우선도</th>
                <th scope="col">매장</th>
                <th scope="col">상품</th>
                <th scope="col">검토 사유·판매 흐름</th>
                <th scope="col">현재 판매가능재고</th>
                <th scope="col" aria-sort={ariaSortFor('SHORTAGE_QUANTITY')}>
                  <button type="button" onClick={() => toggleHeaderSort('SHORTAGE_QUANTITY')}>
                    목표재고 대비 부족
                  </button>
                </th>
                <th scope="col" aria-sort={ariaSortFor('COVERAGE_DAYS')}>
                  <button type="button" onClick={() => toggleHeaderSort('COVERAGE_DAYS')}>
                    재고일수
                  </button>
                </th>
                <th scope="col">확정 입고 예정</th>
                <th scope="col">이동안</th>
                <th scope="col" aria-sort={ariaSortFor('SALES_EXPOSURE')}>
                  <button type="button" onClick={() => toggleHeaderSort('SALES_EXPOSURE')}>
                    매출 노출액(참고)
                  </button>
                </th>
                <th scope="col">검토</th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((item) => (
                <ExceptionRow key={item.inventoryMetricId} item={item} onSelectMetric={onSelectMetric} />
              ))}
            </tbody>
          </table>
        </div>
      )}

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
  const firstBlocking = item.blockingReasons[0]
  const extraBlockingCount = item.blockingReasons.length - 1

  return (
    <tr>
      <td>
        <span className="badge">{workStatusLabel(item.workStatus)}</span>
      </td>
      <td>{severityLabel(item.severity)}</td>
      <td>
        <div>{item.storeName ?? formatOrDash(item.storeId)}</div>
        <div className="exception-list__secondary">{item.region ?? formatOrDash(item.storeId)} · {item.storeId}</div>
      </td>
      <td>
        <div>{item.productName ?? formatOrDash(item.skuId)}</div>
        <div className="exception-list__secondary">
          {[item.color, item.sizeName].filter(Boolean).join(' / ') || '—'} · {item.skuId}
        </div>
      </td>
      <td>
        <div>{demandSignalLabel(item.primaryDemandSignalType)}</div>
        <div className="exception-list__secondary">
          {demandConfidenceLabel(item.demandConfidence)}
          {item.qualityFlags.length > 0 && ` · ${item.qualityFlags.map(qualityFlagLabel).join(', ')}`}
        </div>
      </td>
      <td className="numeric">{formatQuantity(item.availableQuantity)}</td>
      <td className="numeric">{formatQuantity(item.expectedShortageQuantity)}</td>
      <td className="numeric">{formatCoverageDays(item.coverageDays)}</td>
      <td>
        {item.nextConfirmedInboundAt !== null ? (
          <>
            {formatDateTime(item.nextConfirmedInboundAt)}
            {item.upcomingConfirmedInboundQuantity !== null && (
              <span className="exception-list__secondary"> · {formatQuantity(item.upcomingConfirmedInboundQuantity)}개</span>
            )}
          </>
        ) : item.qualityFlags.includes('MISSING_INBOUND') ? (
          '입고 정보 확인 필요'
        ) : (
          '확정 입고 없음'
        )}
      </td>
      <td>
        {item.workStatus === 'COMPLETED' ? (
          '처리 완료'
        ) : item.executableCandidateCount > 0 ? (
          `추가 이동안 ${item.executableCandidateCount}건`
        ) : firstBlocking ? (
          <>
            이동안 없음 · {rejectionReasonLabel(firstBlocking)}
            {extraBlockingCount > 0 && ` 외 ${extraBlockingCount}건`}
          </>
        ) : (
          '이동안 없음'
        )}
      </td>
      <td className="numeric">{formatMoney(item.estimatedSalesImpact)}</td>
      <td>
        {item.inventoryMetricId !== null && (
          <button type="button" onClick={() => onSelectMetric(item.inventoryMetricId as number)}>
            검토하기
          </button>
        )}
      </td>
    </tr>
  )
}

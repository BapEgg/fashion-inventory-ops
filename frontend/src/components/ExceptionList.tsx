import type { InventoryExceptionSummary } from '../types'
import { classificationLabel, coverageDaysLabel, priorityLabel } from '../labels'

interface ExceptionListProps {
  exceptions: InventoryExceptionSummary[]
  onSelect: (inventoryMetricId: number) => void
}

export default function ExceptionList({ exceptions, onSelect }: ExceptionListProps) {
  if (exceptions.length === 0) {
    return <p className="notice">해당 분석일에 재고 예외가 없습니다.</p>
  }

  return (
    <div className="table-scroll">
      <table className="exception-table">
        <thead>
          <tr>
            <th>분류</th>
            <th>우선순위</th>
            <th>매장</th>
            <th>상품</th>
            <th>가용수량</th>
            <th>재고 보유일수</th>
            <th>추천 이동수량</th>
            <th aria-label="상세보기" />
          </tr>
        </thead>
        <tbody>
          {exceptions.map((exception) => (
            <tr key={exception.inventoryMetricId}>
              <td>
                <span className={`badge badge-${exception.classification.toLowerCase()}`}>
                  {classificationLabel(exception.classification)}
                </span>
              </td>
              <td>{priorityLabel(exception.priority)}</td>
              <td>{exception.storeName ?? exception.storeId}</td>
              <td>{exception.productName ?? exception.skuId}</td>
              <td>{exception.availableQuantity}</td>
              <td>{coverageDaysLabel(exception.coverageDays)}</td>
              <td>{exception.recommendedQuantity ?? '-'}</td>
              <td>
                <button type="button" className="link-button" onClick={() => onSelect(exception.inventoryMetricId)}>
                  상세보기
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

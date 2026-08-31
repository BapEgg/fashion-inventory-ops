import type { ObservationWindow } from '../types'
import { formatDate, formatMoney, formatQuantity } from '../formatters'

/**
 * "판매·재고 근거" tab의 28일 판매/재고 evidence, per redesign spec section 8.9. The small SVG
 * trend is presentation only -- its coordinate scaling never changes the underlying values, which
 * are always also available in the accessible table below it.
 */
export function ObservationEvidence({ window }: { window: ObservationWindow }) {
  const days = window.days
  // Missing values stay `null` here -- turning an unknown day into a plotted `0` would visually
  // claim "no sales" where the truth is "no data," so the chart draws a gap instead.
  const soldValues = days.map((d) => d.soldQuantity)
  const availableValues = days.map((d) =>
    d.onHandQuantity !== null && d.reservedQuantity !== null ? d.onHandQuantity - d.reservedQuantity : null,
  )
  const knownValues = [...soldValues, ...availableValues].filter((v): v is number => v !== null)
  const maxValue = Math.max(1, ...knownValues)

  function pathFor(values: (number | null)[]): string {
    if (values.length <= 1) {
      return ''
    }
    let d = ''
    let penDown = false
    values.forEach((value, index) => {
      if (value === null) {
        penDown = false
        return
      }
      const x = (index / (values.length - 1)) * 100
      const y = 40 - (value / maxValue) * 38
      d += penDown ? ` L${x},${y}` : `M${x},${y}`
      penDown = true
    })
    return d
  }

  return (
    <section aria-label="최근 28일 판매량과 판매가능재고">
      <h3>최근 28일 판매량과 판매가능재고</h3>
      <p className="observation-evidence__window">
        {formatDate(window.startDate)} ~ {formatDate(window.endDate)} ({window.dayCount}일)
      </p>

      <svg
        className="observation-evidence__chart"
        viewBox="0 0 100 40"
        role="img"
        aria-label="일별 판매량과 판매가능재고 추이 (참고용, 정확한 값은 아래 표를 확인하세요)"
      >
        <path d={pathFor(availableValues)} fill="none" className="observation-evidence__line observation-evidence__line--available" />
        <path d={pathFor(soldValues)} fill="none" className="observation-evidence__line observation-evidence__line--sold" />
      </svg>
      <ul className="observation-evidence__legend">
        <li className="observation-evidence__legend-item observation-evidence__legend-item--available">판매가능재고</li>
        <li className="observation-evidence__legend-item observation-evidence__legend-item--sold">판매량</li>
      </ul>

      <div className="observation-evidence__scroll">
        <table>
          <caption className="visually-hidden">일자별 재고·판매 상세 근거</caption>
          <thead>
            <tr>
              <th scope="col">날짜</th>
              <th scope="col">실재고</th>
              <th scope="col">예약재고</th>
              <th scope="col">품절</th>
              <th scope="col">판매수량</th>
              <th scope="col">거래건수</th>
              <th scope="col">최대 거래수량</th>
              <th scope="col">평균 판매가</th>
            </tr>
          </thead>
          <tbody>
            {days.map((day, index) => (
              <tr key={day.date ?? index}>
                <td>{formatDate(day.date)}</td>
                <td>{formatQuantity(day.onHandQuantity)}</td>
                <td>{formatQuantity(day.reservedQuantity)}</td>
                <td>{day.outOfStock ? '품절 관측' : '—'}</td>
                <td>{formatQuantity(day.soldQuantity)}</td>
                <td>{formatQuantity(day.transactionCount)}</td>
                <td>{formatQuantity(day.maxTransactionQuantity)}</td>
                <td>{formatMoney(day.averageSellingPrice)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <details className="observation-evidence__source">
        <summary>데이터 출처 보기</summary>
        <div className="observation-evidence__scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">날짜</th>
                <th scope="col">재고 출처</th>
                <th scope="col">판매 출처</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day, index) => (
                <tr key={day.date ?? index}>
                  <td>{formatDate(day.date)}</td>
                  <td>{day.inventorySourceType ?? '—'}</td>
                  <td>{day.salesSourceType ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  )
}

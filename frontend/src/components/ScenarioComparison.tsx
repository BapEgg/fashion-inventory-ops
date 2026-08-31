import type { CandidateDetail, DemandConfidence } from '../types'
import { demandConfidenceLabel, exceptionTypeLabel, scenarioTypeLabel } from '../labels'
import { formatCoverageDays, formatDateTime, formatQuantity } from '../formatters'

const SCENARIO_ORDER = ['NO_ACTION', 'CONSERVATIVE', 'BASE', 'AGGRESSIVE'] as const

/**
 * "이동수량 비교" -- the selected candidate's stored automatic scenarios, in the fixed
 * NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE order, collapsed to the 7 columns spec section 8.8
 * keeps. `warningSummary` is an internal English diagnostic string and is never shown verbatim --
 * only its presence/absence drives a fixed Korean notice. BASE is visually highlighted as the
 * "기준 제안" row.
 */
export function ScenarioComparison({
  candidate,
  demandConfidence,
}: {
  candidate: CandidateDetail
  demandConfidence: DemandConfidence | null
}) {
  const byType = new Map(candidate.scenarios.map((scenario) => [scenario.scenarioType, scenario]))
  const ordered = SCENARIO_ORDER.map((type) => byType.get(type)).filter((s) => s !== undefined)

  if (ordered.length === 0) {
    return <p>저장된 이동수량 비교가 없습니다.</p>
  }

  return (
    <section aria-label="이동수량 비교">
      <h4>이동수량 비교</h4>
      <p className="scenario-comparison__confidence">판단 근거 수준: {demandConfidenceLabel(demandConfidence)}</p>
      <div className="scenario-comparison__scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">기준</th>
              <th scope="col">이동수량</th>
              <th scope="col">입고점 예상재고·재고일수</th>
              <th scope="col">입고점 상태</th>
              <th scope="col">출고점 예상재고·재고일수</th>
              <th scope="col">출고점 상태</th>
              <th scope="col">예상 도착·주의사항</th>
            </tr>
          </thead>
          <tbody>
            {ordered.map((scenario) => (
              <tr
                key={scenario.scenarioId ?? scenario.scenarioType}
                className={scenario.scenarioType === 'BASE' ? 'scenario-comparison__row--base' : undefined}
              >
                <th scope="row">
                  {scenarioTypeLabel(scenario.scenarioType)}
                  {scenario.scenarioType === 'BASE' && <span className="scenario-comparison__base-badge"> 기준 제안</span>}
                </th>
                <td>{formatQuantity(scenario.scenarioQuantity)}</td>
                <td>
                  {formatQuantity(scenario.receiverBeforeAvailable)} → {formatQuantity(scenario.receiverAfterAvailable)}
                  {' · '}
                  {formatCoverageDays(scenario.receiverBeforeCoverage)} → {formatCoverageDays(scenario.receiverAfterCoverage)}
                </td>
                <td>{exceptionTypeLabel(scenario.receiverRiskCode)}</td>
                <td>
                  {formatQuantity(scenario.donorBeforeAvailable)} → {formatQuantity(scenario.donorAfterAvailable)}
                  {' · '}
                  {formatCoverageDays(scenario.donorBeforeCoverage)} → {formatCoverageDays(scenario.donorAfterCoverage)}
                </td>
                <td>{exceptionTypeLabel(scenario.donorRiskCode)}</td>
                <td>
                  {formatDateTime(scenario.expectedArrivalAt)}
                  {scenario.inboundIncluded && ' (입고 반영)'}
                  {scenario.warningSummary && (
                    <div className="scenario-comparison__warning">최소 이동수량·포장단위 조건으로 실행할 수 없는 시나리오입니다</div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

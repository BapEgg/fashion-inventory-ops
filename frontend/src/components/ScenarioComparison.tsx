import type { CandidateDetail, DemandConfidence } from '../types'
import { demandConfidenceLabel, exceptionTypeLabel, scenarioTypeLabel } from '../labels'
import { formatCoverageDays, formatDateTime, formatDemandRate, formatQuantity } from '../formatters'

const SCENARIO_ORDER = ['NO_ACTION', 'CONSERVATIVE', 'BASE', 'AGGRESSIVE'] as const

/**
 * The selected candidate's stored automatic scenarios, in the fixed
 * NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE order, per the React wiring spec section 6.
 * `warningSummary` is an internal English diagnostic string and is never shown verbatim -- only
 * its presence/absence drives a fixed Korean notice.
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
    return <p>저장된 자동 시나리오가 없습니다.</p>
  }

  return (
    <section aria-label="자동 시나리오 비교">
      <h4>자동 시나리오 비교</h4>
      <p className="scenario-comparison__confidence">수요 신뢰도: {demandConfidenceLabel(demandConfidence)}</p>
      <div className="scenario-comparison__scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">시나리오</th>
              <th scope="col">이동수량</th>
              <th scope="col">수요율</th>
              <th scope="col">수령 매장(전/후)</th>
              <th scope="col">수령 보유일수(전/후)</th>
              <th scope="col">수령 위험</th>
              <th scope="col">공급 매장(전/후)</th>
              <th scope="col">공급 보유일수(전/후)</th>
              <th scope="col">공급 위험</th>
              <th scope="col">리드타임 / 예상 도착</th>
              <th scope="col">경고</th>
            </tr>
          </thead>
          <tbody>
            {ordered.map((scenario) => (
              <tr key={scenario.scenarioId ?? scenario.scenarioType}>
                <th scope="row">{scenarioTypeLabel(scenario.scenarioType)}</th>
                <td>{formatQuantity(scenario.scenarioQuantity)}</td>
                <td>{formatDemandRate(scenario.demandRate)}</td>
                <td>
                  {formatQuantity(scenario.receiverBeforeAvailable)} → {formatQuantity(scenario.receiverAfterAvailable)}
                </td>
                <td>
                  {formatCoverageDays(scenario.receiverBeforeCoverage)} → {formatCoverageDays(scenario.receiverAfterCoverage)}
                </td>
                <td>{exceptionTypeLabel(scenario.receiverRiskCode)}</td>
                <td>
                  {formatQuantity(scenario.donorBeforeAvailable)} → {formatQuantity(scenario.donorAfterAvailable)}
                </td>
                <td>
                  {formatCoverageDays(scenario.donorBeforeCoverage)} → {formatCoverageDays(scenario.donorAfterCoverage)}
                </td>
                <td>{exceptionTypeLabel(scenario.donorRiskCode)}</td>
                <td>
                  {scenario.leadTimeDays}일 / {formatDateTime(scenario.expectedArrivalAt)}
                  {scenario.inboundIncluded && ' (입고 반영)'}
                </td>
                <td>
                  {scenario.warningSummary ? '최소 이동수량·포장단위 조건으로 실행할 수 없는 시나리오입니다' : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

import { useState } from 'react'
import { decideRebalance, simulateRebalance } from '../api'
import { decisionStatusLabel } from '../labels'
import type { DecisionStatus, RebalanceSimulationResponse, RecommendationView } from '../types'

interface RecommendationPanelProps {
  recommendation: RecommendationView
  role: 'receiver' | 'donor'
  onDecided: () => void
}

/**
 * One recommendation's simulate-then-decide workflow (business-rules.md sections 4
 * and 6). All quantity math and validation happen in the Backend; this component only
 * sends the user's input and renders what the API returns.
 */
export default function RecommendationPanel({ recommendation, role, onDecided }: RecommendationPanelProps) {
  const [requestedQuantity, setRequestedQuantity] = useState(String(recommendation.recommendedQuantity))
  const [simulation, setSimulation] = useState<RebalanceSimulationResponse | null>(null)
  const [simulating, setSimulating] = useState(false)
  const [simulationError, setSimulationError] = useState<string | null>(null)

  const [reason, setReason] = useState('')
  const [actorLabel, setActorLabel] = useState('')
  const [deciding, setDeciding] = useState(false)
  const [decisionError, setDecisionError] = useState<string | null>(null)

  const roleLabel = role === 'receiver' ? '공급 매장 (보내는 쪽)' : '수요 매장 (받는 쪽)'
  const alreadyDecided = recommendation.decisionStatus !== null

  async function handleSimulate() {
    const quantity = Number(requestedQuantity)
    setSimulationError(null)
    setSimulating(true)
    try {
      const result = await simulateRebalance(recommendation.recommendationId, quantity)
      setSimulation(result)
    } catch (error) {
      setSimulation(null)
      setSimulationError(error instanceof Error ? error.message : '시뮬레이션에 실패했습니다.')
    } finally {
      setSimulating(false)
    }
  }

  async function handleDecide(decisionStatus: DecisionStatus) {
    if (!simulation) return
    setDecisionError(null)
    setDeciding(true)
    try {
      await decideRebalance({
        recommendationId: recommendation.recommendationId,
        decisionStatus,
        selectedQuantity: simulation.requestedQuantity,
        reason,
        actorLabel,
      })
      onDecided()
    } catch (error) {
      setDecisionError(error instanceof Error ? error.message : '결정 저장에 실패했습니다.')
    } finally {
      setDeciding(false)
    }
  }

  return (
    <div className="recommendation-panel">
      <div className="recommendation-summary">
        <p>
          <strong>{roleLabel}</strong>: {recommendation.counterpartStoreName ?? recommendation.counterpartStoreId}
        </p>
        <p>
          수요 부족수량 {recommendation.receiverShortageQuantity} · 공급 이동가능수량{' '}
          {recommendation.donorTransferableQuantity} · 추천 이동수량 {recommendation.recommendedQuantity}
        </p>
        <p>
          결정 상태: <span className={`badge badge-decision-${(recommendation.decisionStatus ?? 'pending').toLowerCase()}`}>
            {decisionStatusLabel(recommendation.decisionStatus)}
          </span>
          {recommendation.decidedQuantity !== null && ` (수량 ${recommendation.decidedQuantity})`}
        </p>
      </div>

      {alreadyDecided ? (
        <p className="notice">이 추천은 이미 결정되어 종결되었습니다. 종결된 결정은 변경할 수 없습니다.</p>
      ) : (
        <>
          <div className="form-row">
            <label>
              이동 요청수량 (1 ~ {recommendation.donorTransferableQuantity})
              <input
                type="number"
                min={1}
                max={recommendation.donorTransferableQuantity}
                value={requestedQuantity}
                onChange={(event) => setRequestedQuantity(event.target.value)}
              />
            </label>
            <button type="button" onClick={handleSimulate} disabled={simulating}>
              {simulating ? '시뮬레이션 중…' : '시뮬레이션'}
            </button>
          </div>
          {simulationError && <p className="error-text">{simulationError}</p>}

          {simulation && (
            <div className="simulation-result">
              <table className="coverage-table">
                <thead>
                  <tr>
                    <th>매장</th>
                    <th>이동 전 가용수량</th>
                    <th>이동 후 가용수량</th>
                    <th>이동 전 보유일수</th>
                    <th>이동 후 보유일수</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>{simulation.receiverBefore.storeName ?? simulation.receiverBefore.storeId} (수요)</td>
                    <td>{simulation.receiverBefore.availableQuantity}</td>
                    <td>{simulation.receiverAfter.availableQuantity}</td>
                    <td>{simulation.receiverBefore.coverageDays ?? '-'}</td>
                    <td>{simulation.receiverAfter.coverageDays ?? '-'}</td>
                  </tr>
                  <tr>
                    <td>{simulation.donorBefore.storeName ?? simulation.donorBefore.storeId} (공급)</td>
                    <td>{simulation.donorBefore.availableQuantity}</td>
                    <td>{simulation.donorAfter.availableQuantity}</td>
                    <td>{simulation.donorBefore.coverageDays ?? '-'}</td>
                    <td>{simulation.donorAfter.coverageDays ?? '-'}</td>
                  </tr>
                </tbody>
              </table>

              <div className="decision-form">
                <label>
                  사유
                  <textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={2} />
                </label>
                <label>
                  담당자
                  <input value={actorLabel} onChange={(event) => setActorLabel(event.target.value)} />
                </label>
                <p className="notice">
                  시뮬레이션한 수량({simulation.requestedQuantity})으로 승인 또는 거절합니다.
                </p>
                <div className="decision-buttons">
                  <button
                    type="button"
                    className="approve"
                    disabled={deciding || reason.trim() === '' || actorLabel.trim() === ''}
                    onClick={() => handleDecide('APPROVED')}
                  >
                    승인
                  </button>
                  <button
                    type="button"
                    className="reject"
                    disabled={deciding || reason.trim() === '' || actorLabel.trim() === ''}
                    onClick={() => handleDecide('REJECTED')}
                  >
                    거절
                  </button>
                </div>
                {decisionError && <p className="error-text">{decisionError}</p>}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

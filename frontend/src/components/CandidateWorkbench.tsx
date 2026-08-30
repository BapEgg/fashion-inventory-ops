import type { CandidateDetail, DecisionStatus } from '../types'
import { candidateStatusLabel, decisionStatusLabel, recommendationModeLabel, rejectionReasonLabel } from '../labels'
import { formatDateTime, formatQuantity } from '../formatters'

const TERMINAL_STATUSES = new Set(['APPROVED', 'REJECTED', 'EXPIRED'])

/**
 * Receiver-side and donor-side candidates, rendered as two separate lists per the React wiring
 * spec section 6. No candidate is auto-selected -- the planner must click one before any
 * simulation/decision target exists.
 */
export function CandidateWorkbench({
  candidatesAsReceiver,
  candidatesAsDonor,
  selectedRecommendationId,
  onSelect,
}: {
  candidatesAsReceiver: CandidateDetail[]
  candidatesAsDonor: CandidateDetail[]
  selectedRecommendationId: number | null
  onSelect: (candidate: CandidateDetail) => void
}) {
  return (
    <section aria-label="공급 후보">
      <h3>공급 후보</h3>
      <CandidateGroup
        title="이 매장이 받는 후보"
        candidates={candidatesAsReceiver}
        selectedRecommendationId={selectedRecommendationId}
        onSelect={onSelect}
      />
      <CandidateGroup
        title="이 매장이 공급하는 후보"
        candidates={candidatesAsDonor}
        selectedRecommendationId={selectedRecommendationId}
        onSelect={onSelect}
      />
    </section>
  )
}

function CandidateGroup({
  title,
  candidates,
  selectedRecommendationId,
  onSelect,
}: {
  title: string
  candidates: CandidateDetail[]
  selectedRecommendationId: number | null
  onSelect: (candidate: CandidateDetail) => void
}) {
  if (candidates.length === 0) {
    return (
      <div className="candidate-group">
        <h4>{title}</h4>
        <p>후보가 없습니다.</p>
      </div>
    )
  }

  return (
    <div className="candidate-group">
      <h4>{title}</h4>
      <div className="candidate-group__scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">상대 매장</th>
              <th scope="col">상태</th>
              <th scope="col">추천 방식</th>
              <th scope="col">추천수량</th>
              <th scope="col">부족수량</th>
              <th scope="col">이동 가능량</th>
              <th scope="col">경로(최소/배수/최대)</th>
              <th scope="col">리드타임</th>
              <th scope="col">평가시각</th>
              <th scope="col">최신 결정</th>
              <th scope="col">선택</th>
            </tr>
          </thead>
          <tbody>
            {candidates.map((candidate) => {
              const rejected = candidate.candidateStatus === 'REJECTED'
              const comparisonOnly = candidate.recommendationMode === 'COMPARISON_ONLY'
              const terminal = candidate.latestDecision
                ? TERMINAL_STATUSES.has(candidate.latestDecision.decisionStatus ?? '')
                : false
              const selected = candidate.recommendationId === selectedRecommendationId
              return (
                <tr key={candidate.recommendationId} className={selected ? 'candidate-row--selected' : undefined}>
                  <td>{candidate.counterpartStoreName ?? candidate.counterpartStoreId}</td>
                  <td>{candidateStatusLabel(candidate.candidateStatus)}</td>
                  <td>
                    {recommendationModeLabel(candidate.recommendationMode)}
                    {comparisonOnly && <div className="candidate-group__note">비교용 후보 (실행 가능한 기본 추천 아님)</div>}
                  </td>
                  <td>{formatQuantity(candidate.recommendedQuantity)}</td>
                  <td>{formatQuantity(candidate.receiverShortageQuantity)}</td>
                  <td>{formatQuantity(candidate.donorTransferableQuantity)}</td>
                  <td>
                    {candidate.route
                      ? `${candidate.route.minimumQuantity} / ${candidate.route.packageMultiple} / ${candidate.route.maximumQuantity}`
                      : '—'}
                  </td>
                  <td>{candidate.route ? `${candidate.route.leadTimeDays}일` : '—'}</td>
                  <td>{formatDateTime(candidate.evaluatedAt)}</td>
                  <td>
                    {candidate.latestDecision ? decisionStatusLabel(candidate.latestDecision.decisionStatus) : '결정 없음'}
                  </td>
                  <td>
                    <button
                      type="button"
                      disabled={candidate.recommendationId === null}
                      onClick={() => onSelect(candidate)}
                    >
                      {selected ? '선택됨' : '선택'}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {candidates.some((c) => c.candidateStatus === 'REJECTED') && (
        <ul className="candidate-group__rejections">
          {candidates
            .filter((c) => c.candidateStatus === 'REJECTED')
            .map((c) => (
              <li key={c.recommendationId}>
                {c.counterpartStoreName ?? c.counterpartStoreId}:{' '}
                {[...c.rejectionReasons]
                  .sort((a, b) => a.reasonOrder - b.reasonOrder)
                  .map((r) => rejectionReasonLabel(r.reasonCode))
                  .join(', ')}
              </li>
            ))}
        </ul>
      )}
    </div>
  )
}

/** `APPROVED`/`REJECTED`/`EXPIRED` -- the statuses that close a candidate to further decisions. */
export function isTerminalDecisionStatus(status: DecisionStatus | null | undefined): boolean {
  return status !== null && status !== undefined && TERMINAL_STATUSES.has(status)
}

export function isCandidateTerminal(candidate: CandidateDetail): boolean {
  return isTerminalDecisionStatus(candidate.latestDecision?.decisionStatus ?? null)
}

export function isCandidateActionable(candidate: CandidateDetail): boolean {
  return candidate.candidateStatus !== 'REJECTED' && !isCandidateTerminal(candidate)
}

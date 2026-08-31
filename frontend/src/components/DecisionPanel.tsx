import { useEffect, useRef, useState } from 'react'
import * as api from '../api'
import { ApiError } from '../api'
import type {
  CandidateDetail,
  DecisionHistoryItem,
  Mvp2DecisionHistoryResponse,
  Mvp2DecisionRequestBody,
  Mvp2RebalanceSimulationResponse,
} from '../types'
import {
  APPROVAL_REASON_CODES,
  HOLD_REASON_CODES,
  REJECT_REASON_CODES,
  decisionActionLabel,
  decisionStatusLabel,
  draftStatusLabel,
  exceptionTypeLabel,
  manualViolationLabel,
  rejectionReasonLabel,
} from '../labels'
import { formatCoverageDays, formatDateTime, formatQuantity } from '../formatters'
import { ProblemAlert } from './ProblemAlert'
import { isTerminalDecisionStatus } from './CandidateWorkbench'
import { ApprovalConfirmDialog, type ApprovalConfirmSummary } from './ApprovalConfirmDialog'

/** Error codes that make the current simulation/pending decision stale, per spec section 9. */
const STALE_OR_TERMINAL_CODES = new Set(['STALE_RECOMMENDATION', 'DECISION_ALREADY_TERMINAL'])

interface RunTuple {
  analysisRunId: number
  inputSnapshotVersion: string
  ruleVersion: string
}

type DecisionAction = 'HELD' | 'APPROVED' | 'REJECTED'

const REASON_CODE_OPTIONS: Record<DecisionAction, readonly { value: string; label: string }[]> = {
  APPROVED: APPROVAL_REASON_CODES,
  HELD: HOLD_REASON_CODES,
  REJECTED: REJECT_REASON_CODES,
}

function sameBody(a: Mvp2DecisionRequestBody, b: Mvp2DecisionRequestBody): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

/**
 * "이동수량 변경"과 이동 승인·보류·반려 처리, per redesign spec sections 9, 12.1. Owns
 * idempotency itself: one key is minted immediately before the first submit of a given payload
 * and reused verbatim for every retry of that exact payload; any field change mints a new key on
 * the next submit instead.
 */
export function DecisionPanel({
  candidate,
  runTuple,
  currentStoreName,
  productName,
  actorLabel,
  onActorLabelChange,
  onRequireDetailRefresh,
  onDecisionSaved,
}: {
  candidate: CandidateDetail
  runTuple: RunTuple
  /** This exception's own store name -- the candidate's counterpart is the other side. */
  currentStoreName: string | null
  productName: string | null
  actorLabel: string
  onActorLabelChange: (value: string) => void
  /** Fires after a decision request succeeds (created or idempotent replay) so the caller can refresh the list, per spec 7.6. */
  onDecisionSaved: () => void
  /** Stale/terminal recovery (section 9.6): re-fetches the owning exception detail from Backend. */
  onRequireDetailRefresh: () => void
}) {
  const [quantityInput, setQuantityInput] = useState('')
  const [simulation, setSimulation] = useState<Mvp2RebalanceSimulationResponse | null>(null)
  const [simulationError, setSimulationError] = useState<ApiError | null>(null)
  const [simulationLoading, setSimulationLoading] = useState(false)

  const [action, setAction] = useState<DecisionAction>('APPROVED')
  const [reasonCode, setReasonCode] = useState('')
  const [reason, setReason] = useState('')
  const [policyException, setPolicyException] = useState(false)
  const [decisionSubmitting, setDecisionSubmitting] = useState(false)
  const [decisionError, setDecisionError] = useState<ApiError | null>(null)
  const [decisionResult, setDecisionResult] = useState<{
    created: boolean
    status: string | null
    sequence: number
    transferDraftId: number | null
  } | null>(null)
  const [showApprovalConfirm, setShowApprovalConfirm] = useState(false)

  const [history, setHistory] = useState<Mvp2DecisionHistoryResponse | null>(null)
  const [historyError, setHistoryError] = useState<ApiError | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)

  // Set the instant `DECISION_ALREADY_TERMINAL` is received -- that error code is itself the
  // authoritative signal, so the form must retire immediately rather than wait on (and stay open
  // if the network drops or the response races) the follow-up history GET fired below.
  const [forcedTerminal, setForcedTerminal] = useState(false)

  const simAbortRef = useRef<AbortController | null>(null)
  const historyAbortRef = useRef<AbortController | null>(null)
  const pendingRef = useRef<{ key: string; body: Mvp2DecisionRequestBody } | null>(null)
  const autoSimulatedForRef = useRef<string | null>(null)
  const recommendationId = candidate.recommendationId

  // Per spec section 4.7/8.5: only an ELIGIBLE + RECOMMENDED, not-yet-terminal candidate is
  // actionable -- a COMPARISON_ONLY or REJECTED candidate never renders this section's decision
  // form regardless of what any recalculated basis would otherwise say.
  const structurallyActionable = candidate.candidateStatus === 'ELIGIBLE' && candidate.recommendationMode === 'RECOMMENDED'

  function fetchHistory(id: number) {
    historyAbortRef.current?.abort()
    const controller = new AbortController()
    historyAbortRef.current = controller
    setHistoryLoading(true)
    setHistoryError(null)
    api
      .getDecisionHistory(id, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setHistory(result)
      })
      .catch((e) => {
        if (api.isAbortError(e)) return
        setHistoryError(e as ApiError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false)
      })
  }

  // A new candidate selection invalidates every in-flight/previous simulation, decision form and
  // pending idempotent request, and loads that candidate's own history.
  useEffect(() => {
    simAbortRef.current?.abort()
    historyAbortRef.current?.abort()
    pendingRef.current = null
    autoSimulatedForRef.current = null
    setQuantityInput(candidate.recommendedQuantity !== null ? String(candidate.recommendedQuantity) : '')
    setSimulation(null)
    setSimulationError(null)
    setAction('APPROVED')
    setReasonCode('')
    setReason('')
    setPolicyException(false)
    setDecisionError(null)
    setDecisionResult(null)
    setShowApprovalConfirm(false)
    setHistory(null)
    setHistoryError(null)
    setForcedTerminal(false)
    if (recommendationId !== null) {
      fetchHistory(recommendationId)
    }
    return () => {
      simAbortRef.current?.abort()
      historyAbortRef.current?.abort()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recommendationId])

  function invalidateSimulation() {
    setSimulation(null)
    setSimulationError(null)
  }

  async function runSimulation(quantity: number) {
    if (recommendationId === null || !Number.isInteger(quantity) || quantity <= 0) {
      return
    }
    simAbortRef.current?.abort()
    const controller = new AbortController()
    simAbortRef.current = controller
    setSimulationLoading(true)
    setSimulationError(null)
    try {
      const result = await api.simulateManualQuantity(
        {
          recommendationId,
          requestedQuantity: quantity,
          analysisRunId: runTuple.analysisRunId,
          inputSnapshotVersion: runTuple.inputSnapshotVersion,
          ruleVersion: runTuple.ruleVersion,
          candidateVersion: candidate.candidateVersion,
        },
        controller.signal,
      )
      if (controller.signal.aborted) return
      setSimulation(result)
    } catch (e) {
      if (api.isAbortError(e)) return
      setSimulation(null)
      setSimulationError(e as ApiError)
    } finally {
      if (!controller.signal.aborted) setSimulationLoading(false)
    }
  }

  function handleSimulate() {
    const quantity = Number(quantityInput)
    void runSimulation(quantity)
  }

  // Spec section 9.1: an actionable candidate's recommended quantity is auto-tested once per
  // (recommendationId, candidateVersion, recommendedQuantity) tuple -- never on every render, and
  // never when the recommendation has nothing to recommend.
  useEffect(() => {
    if (!structurallyActionable || recommendationId === null || !candidate.recommendedQuantity) {
      return
    }
    const key = `${recommendationId}:${candidate.candidateVersion}:${candidate.recommendedQuantity}`
    if (autoSimulatedForRef.current === key) {
      return
    }
    autoSimulatedForRef.current = key
    void runSimulation(candidate.recommendedQuantity)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [structurallyActionable, recommendationId, candidate.candidateVersion, candidate.recommendedQuantity])

  const matchingFeasibleSimulation =
    simulation &&
    simulation.feasible &&
    simulation.recommendationId === recommendationId &&
    simulation.candidateVersion === candidate.candidateVersion &&
    simulation.requestedQuantity === Number(quantityInput)
      ? simulation
      : null

  function buildBody(targetAction: DecisionAction): Mvp2DecisionRequestBody | null {
    if (recommendationId === null) {
      return null
    }
    if (targetAction === 'APPROVED') {
      if (!matchingFeasibleSimulation) {
        return null
      }
      return {
        recommendationId,
        decisionStatus: 'APPROVED',
        selectedQuantity: matchingFeasibleSimulation.requestedQuantity,
        policyException,
        reasonCode: reasonCode || null,
        reason: reason.trim() || null,
        actorLabel,
        analysisRunId: runTuple.analysisRunId,
        inputSnapshotVersion: runTuple.inputSnapshotVersion,
        ruleVersion: runTuple.ruleVersion,
        candidateVersion: candidate.candidateVersion,
      }
    }
    return {
      recommendationId,
      decisionStatus: targetAction,
      selectedQuantity: null,
      policyException: false,
      reasonCode: reasonCode || null,
      reason: reason.trim() || null,
      actorLabel,
      analysisRunId: runTuple.analysisRunId,
      inputSnapshotVersion: runTuple.inputSnapshotVersion,
      ruleVersion: runTuple.ruleVersion,
      candidateVersion: candidate.candidateVersion,
    }
  }

  function reasonRequiredFor(targetAction: DecisionAction): boolean {
    return targetAction !== 'APPROVED' || (matchingFeasibleSimulation?.reasonRequired ?? false) || policyException
  }

  const reasonRequired = reasonRequiredFor(action)
  const reasonFilled = reasonCode.trim().length > 0 && reason.trim().length > 0
  const actorLabelFilled = actorLabel.trim().length > 0
  const readyFor = (targetAction: DecisionAction) =>
    !decisionSubmitting && actorLabelFilled && (!reasonRequiredFor(targetAction) || reasonFilled)
  const canApprove = readyFor('APPROVED') && matchingFeasibleSimulation !== null

  async function submitBody(body: Mvp2DecisionRequestBody, key: string) {
    setDecisionSubmitting(true)
    setDecisionError(null)
    try {
      const result = await api.decide(body, key)
      pendingRef.current = null
      setDecisionResult({
        created: result.created,
        status: result.decisionStatus,
        sequence: result.decisionSequence,
        transferDraftId: result.transferDraftId,
      })
      // The response itself is the authoritative outcome -- a successful APPROVED/REJECTED must
      // retire the form immediately, not wait on (and stay open if it fails or lags) the follow-up
      // history GET below. HELD is never terminal, so it stays actionable.
      if (isTerminalDecisionStatus(result.decisionStatus)) {
        setForcedTerminal(true)
      }
      if (recommendationId !== null) {
        fetchHistory(recommendationId)
      }
      onDecisionSaved()
    } catch (e) {
      if (api.isAbortError(e)) return
      // `api.ts` normalizes every non-abort failure (HTTP ProblemDetail or a raw network throw)
      // into an `ApiError`, so this is never a bare `TypeError` with blank fields.
      const apiError = api.toNetworkError(e)
      setDecisionError(apiError)
      // Only a retryable ProblemDetail (or a raw network failure) keeps the pending request alive
      // for a same-payload retry -- IDEMPOTENCY_KEY_REUSED and every other definite rejection
      // discards it so the next submit always mints a fresh key.
      if (!apiError.retryable) {
        pendingRef.current = null
      }
      // STALE_RECOMMENDATION/DECISION_ALREADY_TERMINAL (section 9.6): the simulation and any
      // client-held candidate/decision state are now stale -- discard the simulation and pull a
      // fresh canonical history so the terminal gate below re-evaluates against current data.
      if (STALE_OR_TERMINAL_CODES.has(apiError.code)) {
        setSimulation(null)
        setSimulationError(null)
        if (apiError.code === 'DECISION_ALREADY_TERMINAL') {
          setForcedTerminal(true)
        }
        if (recommendationId !== null) {
          fetchHistory(recommendationId)
        }
      }
    } finally {
      setDecisionSubmitting(false)
    }
  }

  function submit(targetAction: DecisionAction) {
    const body = buildBody(targetAction)
    if (!body) {
      return
    }
    setShowApprovalConfirm(false)
    if (pendingRef.current && sameBody(pendingRef.current.body, body)) {
      submitBody(pendingRef.current.body, pendingRef.current.key)
      return
    }
    const key = crypto.randomUUID()
    pendingRef.current = { key, body }
    submitBody(body, key)
  }

  function handleApproveClick() {
    setAction('APPROVED')
    if (!canApprove) {
      return
    }
    setShowApprovalConfirm(true)
  }

  function handleHoldOrRejectClick(next: 'HELD' | 'REJECTED') {
    setAction(next)
    if (!readyFor(next)) {
      return
    }
    submit(next)
  }

  // The canonical history GET is the freshest source of truth for the terminal gate -- the
  // `candidate` prop is a snapshot from the last full detail fetch and can go stale the moment
  // this or any other session decides the recommendation. Fall back to the prop only until the
  // first history response lands.
  const effectiveStatus = history?.currentStatus ?? candidate.latestDecision?.decisionStatus ?? null
  const terminal = forcedTerminal || isTerminalDecisionStatus(effectiveStatus)
  const actionable = structurallyActionable && !terminal

  const isReceiverSide = candidate.direction === 'RECEIVER'
  const donorStoreName = isReceiverSide ? candidate.counterpartStoreName ?? candidate.counterpartStoreId ?? '—' : currentStoreName ?? '—'
  const receiverStoreName = isReceiverSide ? currentStoreName ?? '—' : candidate.counterpartStoreName ?? candidate.counterpartStoreId ?? '—'

  const approvalSummary: ApprovalConfirmSummary | null =
    matchingFeasibleSimulation && matchingFeasibleSimulation.projection
      ? {
          donorStoreName,
          receiverStoreName,
          productName: productName ?? '—',
          quantity: matchingFeasibleSimulation.requestedQuantity,
          expectedArrivalDate: matchingFeasibleSimulation.projection.expectedArrivalDate,
          receiverBeforeAvailable: matchingFeasibleSimulation.projection.receiverBeforeAvailable,
          receiverAfterAvailable: matchingFeasibleSimulation.projection.receiverAfterAvailable,
          receiverAfterCoverageDays: matchingFeasibleSimulation.projection.receiverAfterCoverageDays,
          donorBeforeAvailable: matchingFeasibleSimulation.projection.donorBeforeAvailable,
          donorAfterAvailable: matchingFeasibleSimulation.projection.donorAfterAvailable,
          donorAfterCoverageDays: matchingFeasibleSimulation.projection.donorAfterCoverageDays,
        }
      : null

  if (!structurallyActionable && !terminal) {
    // comparison-only/rejected -- this section renders nothing; ExceptionDetail shows the
    // "이동 불가·원인 확인 안내" panel instead (spec section 8.6).
    return null
  }

  return (
    <section aria-label="이동 승인·보류·반려" className="decision-panel">
      {terminal ? (
        <p>이미 처리 완료된 이동안입니다.</p>
      ) : (
        <>
          <div className="decision-panel__quantity">
            <label>
              추가 이동수량
              <input
                type="number"
                min={1}
                step={1}
                value={quantityInput}
                onChange={(e) => {
                  setQuantityInput(e.target.value)
                  invalidateSimulation()
                }}
              />
            </label>
            <button type="button" onClick={handleSimulate} disabled={simulationLoading}>
              {simulationLoading ? '확인 중…' : '변경 결과 확인'}
            </button>
          </div>

          {simulationLoading && !simulation && <p aria-live="polite">추가 이동 제안을 확인하고 있습니다…</p>}
          {simulationError && <ProblemAlert error={simulationError} onRetry={simulationError.retryable ? handleSimulate : undefined} />}

          {simulation && <SimulationResult simulation={simulation} onCopySuggested={(v) => setQuantityInput(String(v))} />}

          {/* HELD/REJECTED stay clickable even before their reason is filled in -- per spec section
              9.3 the first click declares intent and reveals the (required) 메모 추가 fields; only
              a genuinely unmet precondition (담당자명/사유) blocks the actual submit, and that is
              surfaced via the hint text below rather than a disabled button the planner cannot
              interact with to discover why. 이동 승인 is the one button a click can never make
              ready on its own (its precondition is the auto/asked-for simulation, not local form
              state), so it alone stays disabled until `canApprove`. */}
          <div className="decision-panel__actions">
            <button type="button" onClick={handleApproveClick} disabled={!canApprove || decisionSubmitting}>
              {decisionActionLabel('APPROVED')}
            </button>
            <button type="button" onClick={() => handleHoldOrRejectClick('HELD')} disabled={decisionSubmitting}>
              {decisionActionLabel('HELD')}
            </button>
            <button type="button" onClick={() => handleHoldOrRejectClick('REJECTED')} disabled={decisionSubmitting}>
              {decisionActionLabel('REJECTED')}
            </button>
          </div>
          {(action === 'HELD' || action === 'REJECTED') && !readyFor(action) && (
            <p className="decision-panel__hint">
              {!actorLabelFilled ? '담당자명을 입력해야 처리할 수 있습니다.' : '사유와 처리 메모를 입력해야 처리할 수 있습니다.'}
            </p>
          )}
          {!canApprove && (
            <p className="decision-panel__hint">
              {matchingFeasibleSimulation === null
                ? '현재 수량과 일치하는 실행 가능한 확인 결과가 있어야 이동 승인할 수 있습니다.'
                : !actorLabelFilled
                  ? '담당자명을 입력해야 처리할 수 있습니다.'
                  : '처리 메모를 입력해야 처리할 수 있습니다.'}
            </p>
          )}

          <label className="decision-panel__checkbox">
            <input type="checkbox" checked={policyException} onChange={(e) => setPolicyException(e.target.checked)} />
            정책 예외로 승인
          </label>
          {policyException && (
            <p className="decision-panel__hint">정책 예외는 사유 기록 방식이며 재고·경로·최신성 제약을 우회하지 않습니다.</p>
          )}

          <details className="decision-panel__reason" open={reasonRequired}>
            <summary>메모 추가{reasonRequired && ' (필수)'}</summary>
            <label>
              사유
              <select value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
                <option value="">선택하세요</option>
                {REASON_CODE_OPTIONS[action].map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              처리 메모
              <textarea
                value={reason}
                placeholder="무엇을 확인했거나 왜 수량을 바꿨는지 입력하세요"
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          </details>

          <label>
            담당자명 (인증 연결 전 데모 입력값)
            <input
              type="text"
              value={actorLabel}
              maxLength={100}
              onChange={(e) => onActorLabelChange(e.target.value)}
            />
          </label>
        </>
      )}

      {showApprovalConfirm && approvalSummary && (
        <ApprovalConfirmDialog
          summary={approvalSummary}
          submitting={decisionSubmitting}
          onCancel={() => setShowApprovalConfirm(false)}
          onConfirm={() => submit('APPROVED')}
        />
      )}

      {/* Rendered outside the terminal branch above: a successful terminal decision or a
          DECISION_ALREADY_TERMINAL error both force `terminal` true in the very same render, which
          would otherwise hide the success message / error alert right when the user most needs to
          see them. */}
      {decisionResult && (
        <p role="status" aria-live="polite">
          {decisionResultMessage(decisionResult)}
        </p>
      )}
      {decisionError && (
        <>
          <ProblemAlert error={decisionError} onRetry={decisionError.retryable ? () => submit(action) : undefined} />
          {decisionError.code === 'STALE_RECOMMENDATION' && (
            <p>재고 상황이 바뀌었습니다. 최신 내용을 불러온 뒤 다시 검토하세요.</p>
          )}
          {STALE_OR_TERMINAL_CODES.has(decisionError.code) && (
            <button type="button" onClick={onRequireDetailRefresh}>
              최신 내용 불러오기
            </button>
          )}
        </>
      )}

      <DecisionHistoryView
        history={history}
        loading={historyLoading}
        error={historyError}
        onRetry={recommendationId !== null ? () => fetchHistory(recommendationId) : undefined}
      />
    </section>
  )
}

function decisionResultMessage(result: { created: boolean; status: string | null; transferDraftId: number | null }): string {
  if (!result.created) {
    return '이미 처리된 동일 요청 결과를 불러왔습니다.'
  }
  if (result.status === 'APPROVED') {
    return result.transferDraftId !== null
      ? `이동 승인 완료 · ERP 이동요청 초안 #${result.transferDraftId}가 생성되었습니다.`
      : '이동 승인 완료'
  }
  if (result.status === 'HELD') {
    return '보류로 저장했습니다. 확인 후 다시 처리할 수 있습니다.'
  }
  if (result.status === 'REJECTED') {
    return '이동안을 반려했습니다.'
  }
  return '처리 결과가 저장되었습니다.'
}

function SimulationResult({
  simulation,
  onCopySuggested,
}: {
  simulation: Mvp2RebalanceSimulationResponse
  onCopySuggested: (value: number) => void
}) {
  return (
    <div className="decision-panel__simulation" aria-live="polite">
      {!simulation.feasible ? (
        <div>
          <p>이 수량으로는 이동할 수 없습니다.</p>
          <ul>
            {simulation.violations.map((v) => (
              <li key={v}>{manualViolationLabel(v)}</li>
            ))}
          </ul>
          {simulation.candidateRejectionReasons.length > 0 && (
            <ul>
              {simulation.candidateRejectionReasons.map((r) => (
                <li key={r}>{rejectionReasonLabel(r)}</li>
              ))}
            </ul>
          )}
          <p>최대 가능수량: {formatQuantity(simulation.maximumFeasibleQuantity)}</p>
          <p>
            하향 제안수량: {formatQuantity(simulation.suggestedQuantity)}{' '}
            {simulation.suggestedQuantity > 0 && (
              <button type="button" onClick={() => onCopySuggested(simulation.suggestedQuantity)}>
                입력란에 적용
              </button>
            )}
          </p>
          <details>
            <summary>반영 내역 보기</summary>
            <p>
              경로 최소 {simulation.routeMinimumQuantity} / 포장단위 {simulation.packageMultiple} / 최대{' '}
              {simulation.routeMaximumQuantity} · 출고 가능 {formatQuantity(simulation.donorTransferableQuantity)} · 수용
              여력 {formatQuantity(simulation.receiverCapacityRemaining)}
            </p>
          </details>
        </div>
      ) : (
        simulation.projection && (
          <div>
            <p>이 수량으로 이동 가능</p>
            <dl>
              <div>
                <dt>입고점 판매가능재고(전 → 후)</dt>
                <dd>
                  {formatQuantity(simulation.projection.receiverBeforeAvailable)} →{' '}
                  {formatQuantity(simulation.projection.receiverAfterAvailable)}
                </dd>
              </div>
              <div>
                <dt>입고점 재고일수(전 → 후)</dt>
                <dd>
                  {formatCoverageDays(simulation.projection.receiverBeforeCoverageDays)} →{' '}
                  {formatCoverageDays(simulation.projection.receiverAfterCoverageDays)}
                </dd>
              </div>
              <div>
                <dt>출고점 판매가능재고(전 → 후)</dt>
                <dd>
                  {formatQuantity(simulation.projection.donorBeforeAvailable)} →{' '}
                  {formatQuantity(simulation.projection.donorAfterAvailable)}
                </dd>
              </div>
              <div>
                <dt>출고점 재고일수(전 → 후)</dt>
                <dd>
                  {formatCoverageDays(simulation.projection.donorBeforeCoverageDays)} →{' '}
                  {formatCoverageDays(simulation.projection.donorAfterCoverageDays)}
                </dd>
              </div>
              <div>
                <dt>예상 도착일</dt>
                <dd>{simulation.projection.expectedArrivalDate ?? '—'}</dd>
              </div>
            </dl>
            <details>
              <summary>반영 내역 보기</summary>
              <p>
                입고점 확정입고 {formatQuantity(simulation.projection.receiverInboundArrivingBeforeTransfer)} · 입고점
                진행중 입고 {formatQuantity(simulation.projection.receiverOpenTransferInbound)} · 입고점 진행중 출고{' '}
                {formatQuantity(simulation.projection.receiverOpenTransferOutbound)} · 출고점 확정입고{' '}
                {formatQuantity(simulation.projection.donorInboundArrivingBeforeDispatch)} · 출고점 진행중 출고{' '}
                {formatQuantity(simulation.projection.donorOpenTransferOutbound)} · 출고점 기승인 초안{' '}
                {formatQuantity(simulation.projection.donorAlreadyApprovedDraftQuantity)}
              </p>
              <p>{simulation.projection.receiverRiskCode && `입고점 위험: ${exceptionTypeLabel(simulation.projection.receiverRiskCode)}`}</p>
              <p>{simulation.projection.donorRiskCode && `출고점 위험: ${exceptionTypeLabel(simulation.projection.donorRiskCode)}`}</p>
            </details>
          </div>
        )
      )}
      <p className="decision-panel__hint">승인 시 최신 근거로 다시 확인합니다.</p>
    </div>
  )
}

function DecisionHistoryView({
  history,
  loading,
  error,
  onRetry,
}: {
  history: Mvp2DecisionHistoryResponse | null
  loading: boolean
  error: ApiError | null
  onRetry?: () => void
}) {
  return (
    <section aria-label="처리 이력" aria-live="polite">
      <h3>처리 이력</h3>
      {loading && <p>이력을 불러오는 중입니다…</p>}
      {error && <ProblemAlert error={error} onRetry={error.retryable ? onRetry : undefined} />}
      {history && (
        <ol className="decision-history">
          <li>현재 상태: {decisionStatusLabel(history.currentStatus)}</li>
          {history.decisions.map((item) => (
            <DecisionHistoryRow key={item.decisionId ?? item.decisionSequence} item={item} />
          ))}
        </ol>
      )}
    </section>
  )
}

function DecisionHistoryRow({ item }: { item: DecisionHistoryItem }) {
  const [open, setOpen] = useState(false)
  return (
    <li className="decision-history__item">
      <div>
        {decisionStatusLabel(item.decisionStatus)}
        {item.selectedQuantity !== null && ` · 수량 ${formatQuantity(item.selectedQuantity)}`}
        {' · '}
        {item.actorLabel ?? '—'}
        {' · '}
        {formatDateTime(item.decidedAt)}
        {item.reason && ` · ${item.reason}`}
      </div>
      {(item.approvalBasis || item.transferDraft) && (
        <>
          <button type="button" className="decision-history__audit-toggle" onClick={() => setOpen((v) => !v)}>
            {open ? '감사정보 숨기기' : '감사정보 보기'}
          </button>
          {open && (
            <div className="decision-history__audit">
              {item.approvalBasis && (
                <dl>
                  <div>
                    <dt>승인 근거 ID</dt>
                    <dd>{item.approvalBasis.approvalBasisId ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>버전 tuple (run/입력/규칙/후보)</dt>
                    <dd>
                      {item.approvalBasis.analysisRunId} / {item.approvalBasis.inputSnapshotVersion ?? '—'} /{' '}
                      {item.approvalBasis.ruleVersion ?? '—'} / {item.approvalBasis.candidateVersion}
                    </dd>
                  </div>
                  <div>
                    <dt>추천 기준 수량</dt>
                    <dd>{formatQuantity(item.approvalBasis.recommendedBaseQuantity)}</dd>
                  </div>
                  <div>
                    <dt>산정 시각</dt>
                    <dd>{formatDateTime(item.approvalBasis.createdAt)}</dd>
                  </div>
                </dl>
              )}
              {item.transferDraft && (
                <dl>
                  <div>
                    <dt>ERP 이동요청 초안 ID / 상태</dt>
                    <dd>
                      #{item.transferDraft.transferDraftId ?? '—'} · {draftStatusLabel(item.transferDraft.draftStatus)}{' '}
                      (실제 ERP 접수·출고 완료를 의미하지 않습니다)
                    </dd>
                  </div>
                  <div>
                    <dt>출고 → 입고 매장</dt>
                    <dd>
                      {item.transferDraft.donorStoreId ?? '—'} → {item.transferDraft.receiverStoreId ?? '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>SKU / 수량</dt>
                    <dd>
                      {item.transferDraft.skuId ?? '—'} / {formatQuantity(item.transferDraft.quantity)}
                    </dd>
                  </div>
                  <div>
                    <dt>생성 / 갱신 시각</dt>
                    <dd>
                      {formatDateTime(item.transferDraft.createdAt)} / {formatDateTime(item.transferDraft.updatedAt)}
                    </dd>
                  </div>
                </dl>
              )}
            </div>
          )}
        </>
      )}
    </li>
  )
}

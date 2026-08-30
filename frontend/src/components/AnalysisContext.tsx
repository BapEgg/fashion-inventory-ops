import { useEffect, useRef, useState } from 'react'
import * as api from '../api'
import type { ApiError } from '../api'
import type { AnalysisRunStatus } from '../types'
import { ProblemAlert } from './ProblemAlert'
import { analysisRunStatusLabel } from '../labels'
import { formatDateTime } from '../formatters'

const PRESET_ANALYSIS_DATE = '2026-09-30'
const PRESET_INPUT_SNAPSHOT_VERSION = 'MVP-2-GS-V1'
const POLL_INTERVAL_MS = 1500
const POLL_MAX_ATTEMPTS = 40

export interface RunContext {
  analysisRunId: number
  analysisDate: string | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  status: AnalysisRunStatus
  completedAt: string | null
}

type Phase = 'idle' | 'launching' | 'polling' | 'completed' | 'failed' | 'timeout'

interface RunLike {
  analysisRunId: number | null
  analysisDate: string | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  status: AnalysisRunStatus | null
  completedAt: string | null
}

function toRunContext(response: RunLike, fallbackRunId?: number): RunContext | null {
  const analysisRunId = response.analysisRunId ?? fallbackRunId ?? null
  if (analysisRunId === null || response.status === null) {
    return null
  }
  return {
    analysisRunId,
    analysisDate: response.analysisDate,
    inputSnapshotVersion: response.inputSnapshotVersion,
    ruleVersion: response.ruleVersion,
    status: response.status,
    completedAt: response.completedAt,
  }
}

/**
 * The compact analysis toolbar, per the React wiring spec section 3 -- not a hero/card, just the
 * control that changes the current data context. Owns its own launch/poll lifecycle; only tells
 * the parent about a COMPLETED run via {@link onRunCompleted}, since the parent (not this
 * component) resets the downstream list/detail/decision state on a new run.
 */
export function AnalysisContext({
  onRunStarting,
  onRunCompleted,
}: {
  /** Fires once a launch actually begins (after input validation passes), before the POST. */
  onRunStarting: () => void
  onRunCompleted: (run: RunContext, alreadyCompleted: boolean) => void
}) {
  const [analysisDate, setAnalysisDate] = useState(PRESET_ANALYSIS_DATE)
  const [inputSnapshotVersion, setInputSnapshotVersion] = useState(PRESET_INPUT_SNAPSHOT_VERSION)
  const [inputError, setInputError] = useState<string | null>(null)
  const [phase, setPhase] = useState<Phase>('idle')
  const [run, setRun] = useState<RunContext | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [justCompletedMessage, setJustCompletedMessage] = useState<string | null>(null)

  const abortRef = useRef<AbortController | null>(null)
  const pollTimerRef = useRef<number | null>(null)

  function abortInFlight() {
    abortRef.current?.abort()
    if (pollTimerRef.current !== null) {
      window.clearTimeout(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }

  useEffect(() => () => abortInFlight(), [])

  function validateInputSnapshotVersion(value: string): string | null {
    if (value.trim().length === 0) {
      return '입력 스냅샷 버전을 입력해 주세요.'
    }
    if (value !== value.trim()) {
      return '입력 스냅샷 버전 앞뒤에는 공백을 넣지 마세요.'
    }
    if (value.length > 64) {
      return '입력 스냅샷 버전은 최대 64자까지 입력할 수 있습니다.'
    }
    return null
  }

  function schedulePoll(analysisRunId: number, attempt: number, controller: AbortController) {
    pollTimerRef.current = window.setTimeout(() => {
      if (controller.signal.aborted) {
        return
      }
      api
        .getAnalysisStatus(analysisRunId, controller.signal)
        .then((status) => {
          if (controller.signal.aborted) {
            return
          }
          const context = toRunContext(status, analysisRunId)
          if (!context) {
            setPhase('failed')
            return
          }
          setRun(context)
          if (context.status === 'COMPLETED') {
            setPhase('completed')
            setJustCompletedMessage('분석이 완료되었습니다.')
            onRunCompleted(context, false)
          } else if (context.status === 'FAILED') {
            setPhase('failed')
          } else if (attempt + 1 >= POLL_MAX_ATTEMPTS) {
            setPhase('timeout')
          } else {
            schedulePoll(analysisRunId, attempt + 1, controller)
          }
        })
        .catch((e) => {
          if (api.isAbortError(e)) {
            return
          }
          setPhase('failed')
          setError(e as ApiError)
        })
    }, POLL_INTERVAL_MS)
  }

  async function handleRunAnalysis() {
    const validation = validateInputSnapshotVersion(inputSnapshotVersion)
    setInputError(validation)
    if (validation) {
      return
    }

    abortInFlight()
    const controller = new AbortController()
    abortRef.current = controller
    setError(null)
    setJustCompletedMessage(null)
    setPhase('launching')
    setRun(null)
    onRunStarting()

    try {
      const response = await api.runAnalysis({ analysisDate, inputSnapshotVersion }, controller.signal)
      if (controller.signal.aborted) {
        return
      }
      const context = toRunContext(response)
      if (!context) {
        setPhase('failed')
        return
      }
      setRun(context)
      if (context.status === 'COMPLETED') {
        setPhase('completed')
        setJustCompletedMessage(response.alreadyCompleted ? '기존 완료 결과를 불러왔습니다.' : '분석이 완료되었습니다.')
        onRunCompleted(context, response.alreadyCompleted)
      } else if (context.status === 'FAILED') {
        setPhase('failed')
      } else {
        setPhase('polling')
        schedulePoll(context.analysisRunId, 0, controller)
      }
    } catch (e) {
      if (api.isAbortError(e)) {
        return
      }
      setPhase('failed')
      setError(e as ApiError)
    }
  }

  function handleManualStatusRefresh() {
    if (!run) {
      return
    }
    abortInFlight()
    const controller = new AbortController()
    abortRef.current = controller
    api
      .getAnalysisStatus(run.analysisRunId, controller.signal)
      .then((status) => {
        if (controller.signal.aborted) {
          return
        }
        const context = toRunContext(status, run.analysisRunId)
        if (!context) {
          return
        }
        setRun(context)
        if (context.status === 'COMPLETED') {
          setPhase('completed')
          setJustCompletedMessage('분석이 완료되었습니다.')
          onRunCompleted(context, false)
        } else if (context.status === 'FAILED') {
          setPhase('failed')
        }
      })
      .catch((e) => {
        if (api.isAbortError(e)) {
          return
        }
        setError(e as ApiError)
      })
  }

  const busy = phase === 'launching' || phase === 'polling'

  return (
    <section className="analysis-toolbar" aria-label="분석 실행">
      <div className="analysis-toolbar__inputs">
        <label>
          분석 기준일
          <input
            type="date"
            value={analysisDate}
            disabled={busy}
            onChange={(e) => setAnalysisDate(e.target.value)}
          />
        </label>
        <label>
          입력 스냅샷 버전
          <input
            type="text"
            value={inputSnapshotVersion}
            disabled={busy}
            maxLength={64}
            onChange={(e) => setInputSnapshotVersion(e.target.value)}
          />
        </label>
        <span className="analysis-toolbar__preset-note">데모 preset</span>
        <button type="button" onClick={handleRunAnalysis} disabled={busy}>
          {busy ? '실행 중…' : '분석 실행'}
        </button>
      </div>

      {inputError && (
        <p className="analysis-toolbar__input-error" role="alert">
          {inputError}
        </p>
      )}

      <div aria-live="polite" className="analysis-toolbar__status">
        {phase === 'polling' && <p>분석이 진행 중입니다. 자동으로 상태를 확인하고 있습니다…</p>}
        {phase === 'timeout' && (
          <div>
            <p>계속 실행 중입니다.</p>
            <button type="button" onClick={handleManualStatusRefresh}>
              상태 새로고침
            </button>
          </div>
        )}
        {phase === 'failed' && <p>분석 실행에 실패했습니다.</p>}
        {justCompletedMessage && phase === 'completed' && <p>{justCompletedMessage}</p>}
      </div>

      {error && <ProblemAlert error={error} onRetry={error.retryable ? handleRunAnalysis : undefined} />}

      {run && (
        <dl className="analysis-toolbar__context">
          <div>
            <dt>run ID</dt>
            <dd>{run.analysisRunId}</dd>
          </div>
          <div>
            <dt>기준일</dt>
            <dd>{run.analysisDate ?? '—'}</dd>
          </div>
          <div>
            <dt>입력 버전</dt>
            <dd>{run.inputSnapshotVersion ?? '—'}</dd>
          </div>
          <div>
            <dt>규칙 버전</dt>
            <dd>{run.ruleVersion ?? '—'}</dd>
          </div>
          <div>
            <dt>상태</dt>
            <dd>{analysisRunStatusLabel(run.status)}</dd>
          </div>
          <div>
            <dt>완료 시각</dt>
            <dd>{formatDateTime(run.completedAt)}</dd>
          </div>
        </dl>
      )}
    </section>
  )
}

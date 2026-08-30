import { useEffect, useRef, useState } from 'react'
import * as api from './api'
import type { ApiError } from './api'
import type { ExceptionListFilters, Mvp2InventoryExceptionPage } from './types'
import { AnalysisContext, type RunContext } from './components/AnalysisContext'
import { DEFAULT_FILTERS, ExceptionFilters } from './components/ExceptionFilters'
import { ExceptionList } from './components/ExceptionList'
import { ExceptionDetail } from './components/ExceptionDetail'
import { ProblemAlert } from './components/ProblemAlert'

const ACTOR_LABEL_STORAGE_KEY = 'stockpilot.actorLabel'

function readStoredActorLabel(): string {
  try {
    return window.sessionStorage.getItem(ACTOR_LABEL_STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

/**
 * Owns only run context and list/detail top-level state, per the React wiring spec section 11.
 * No router/global store -- every screen transition is explicit local state here.
 */
function App() {
  const [run, setRun] = useState<RunContext | null>(null)
  const [filters, setFilters] = useState<ExceptionListFilters>(DEFAULT_FILTERS)
  const [page, setPage] = useState<Mvp2InventoryExceptionPage | null>(null)
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState<ApiError | null>(null)
  const [selectedMetricId, setSelectedMetricId] = useState<number | null>(null)
  const [actorLabel, setActorLabel] = useState<string>(readStoredActorLabel)

  const listAbortRef = useRef<AbortController | null>(null)

  useEffect(() => () => listAbortRef.current?.abort(), [])

  function persistActorLabel(value: string) {
    setActorLabel(value)
    try {
      window.sessionStorage.setItem(ACTOR_LABEL_STORAGE_KEY, value)
    } catch {
      // sessionStorage may be unavailable (private mode); the label just stays in-memory for this render.
    }
  }

  function fetchList(analysisRunId: number, nextFilters: ExceptionListFilters) {
    listAbortRef.current?.abort()
    const controller = new AbortController()
    listAbortRef.current = controller
    setListLoading(true)
    setListError(null)
    api
      .listExceptions(analysisRunId, nextFilters, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setPage(result)
      })
      .catch((e) => {
        if (api.isAbortError(e)) return
        setListError(e as ApiError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false)
      })
  }

  // Retires the previous run's work context: aborts any in-flight list request and clears the
  // run/list/page/detail state so the old queue/detail/decision form cannot stay interactive.
  function retireWorkContext() {
    listAbortRef.current?.abort()
    setRun(null)
    setFilters(DEFAULT_FILTERS)
    setPage(null)
    setListError(null)
    setSelectedMetricId(null)
  }

  // A brand-new launch must retire the prior work context immediately -- not only once it
  // completes -- so the previous run's queue/detail/decision form cannot remain interactive while
  // the new run is still launching or polling.
  function handleRunStarting() {
    retireWorkContext()
  }

  // A new/replayed COMPLETED run resets the previous list, page, selected metric/candidate and
  // any pending decision -- per section 1. Closing the detail view (selectedMetricId -> null)
  // never re-fetches the list; the previously loaded page stays exactly as it was.
  function handleRunCompleted(nextRun: RunContext) {
    retireWorkContext()
    setRun(nextRun)
    fetchList(nextRun.analysisRunId, DEFAULT_FILTERS)
  }

  function handleApplyFilters(next: ExceptionListFilters) {
    setFilters(next)
    setSelectedMetricId(null)
    if (run) {
      fetchList(run.analysisRunId, next)
    }
  }

  function handleResetFilters() {
    setFilters(DEFAULT_FILTERS)
    setSelectedMetricId(null)
    if (run) {
      fetchList(run.analysisRunId, DEFAULT_FILTERS)
    }
  }

  function handlePageChange(nextPage: number) {
    const next = { ...filters, page: nextPage }
    setFilters(next)
    setSelectedMetricId(null)
    if (run) {
      fetchList(run.analysisRunId, next)
    }
  }

  function retryList() {
    if (run) {
      fetchList(run.analysisRunId, filters)
    }
  }

  return (
    <div className="app">
      <p className="app__banner">SYNTHETIC 데이터 · ASSUMPTION 데모 정책 · 실제 F&amp;F 정책 또는 검증된 산업 표준 아님</p>
      <h1>StockPilot 재고 배분 워크벤치</h1>

      <AnalysisContext onRunStarting={handleRunStarting} onRunCompleted={handleRunCompleted} />

      {selectedMetricId === null && run && run.status === 'COMPLETED' && (
        <>
          <ExceptionFilters filters={filters} onApply={handleApplyFilters} onReset={handleResetFilters} />
          {listLoading && (
            <p role="status" aria-live="polite">
              목록을 불러오는 중입니다…
            </p>
          )}
          {listError && <ProblemAlert error={listError} onRetry={listError.retryable ? retryList : undefined} />}
          {page && (
            <ExceptionList
              page={page}
              onSelectMetric={setSelectedMetricId}
              onPageChange={handlePageChange}
              onResetFilters={handleResetFilters}
            />
          )}
        </>
      )}

      {selectedMetricId !== null && (
        <ExceptionDetail
          inventoryMetricId={selectedMetricId}
          onClose={() => setSelectedMetricId(null)}
          actorLabel={actorLabel}
          onActorLabelChange={persistActorLabel}
        />
      )}
    </div>
  )
}

export default App

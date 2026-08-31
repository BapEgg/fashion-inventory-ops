import { useEffect, useRef, useState } from 'react'
import * as api from './api'
import type { ApiError } from './api'
import type { ExceptionListFilters, ExceptionSortKey, Mvp2InventoryExceptionPage, SortDirection } from './types'
import { AnalysisContext, type RunContext } from './components/AnalysisContext'
import { DEFAULT_FILTERS, ExceptionFilters } from './components/ExceptionFilters'
import { ExceptionList } from './components/ExceptionList'
import { ExceptionDetail } from './components/ExceptionDetail'
import { ProblemAlert } from './components/ProblemAlert'
import { WorkQueueSummary } from './components/WorkQueueSummary'
import { WorkStatusTabs, type WorkStatusTabValue } from './components/WorkStatusTabs'

const ACTOR_LABEL_STORAGE_KEY = 'stockpilot.actorLabel'

function readStoredActorLabel(): string {
  try {
    return window.sessionStorage.getItem(ACTOR_LABEL_STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

/**
 * Owns only run context and list/detail top-level state, per the redesign spec section 6.1's
 * master-detail layout. No router/global store -- every screen transition is explicit local state
 * here.
 */
function App() {
  const [run, setRun] = useState<RunContext | null>(null)
  const [filters, setFilters] = useState<ExceptionListFilters>(DEFAULT_FILTERS)
  const [page, setPage] = useState<Mvp2InventoryExceptionPage | null>(null)
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState<ApiError | null>(null)
  const [selectedMetricId, setSelectedMetricId] = useState<number | null>(null)
  const [actorLabel, setActorLabel] = useState<string>(readStoredActorLabel)
  const [listStatusMessage, setListStatusMessage] = useState<string | null>(null)

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

  // The very first query after a run completes: if the default "이동 결정 필요" tab is empty,
  // fall back to "전체" exactly once rather than showing a blank worklist by default (spec 4.8).
  async function fetchInitialList(analysisRunId: number) {
    listAbortRef.current?.abort()
    const controller = new AbortController()
    listAbortRef.current = controller
    setListLoading(true)
    setListError(null)
    try {
      const first = await api.listExceptions(analysisRunId, DEFAULT_FILTERS, controller.signal)
      if (controller.signal.aborted) return
      if (first.summary.decisionRequiredCount === 0) {
        const fallback: ExceptionListFilters = { ...DEFAULT_FILTERS, workStatus: [] }
        const second = await api.listExceptions(analysisRunId, fallback, controller.signal)
        if (controller.signal.aborted) return
        setFilters(fallback)
        setPage(second)
      } else {
        setFilters(DEFAULT_FILTERS)
        setPage(first)
      }
    } catch (e) {
      if (api.isAbortError(e)) return
      setListError(e as ApiError)
    } finally {
      if (!controller.signal.aborted) setListLoading(false)
    }
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
    setListStatusMessage(null)
  }

  // A brand-new launch must retire the prior work context immediately -- not only once it
  // completes -- so the previous run's queue/detail/decision form cannot remain interactive while
  // the new run is still launching or polling.
  function handleRunStarting() {
    retireWorkContext()
  }

  function handleRunCompleted(nextRun: RunContext) {
    retireWorkContext()
    setRun(nextRun)
    void fetchInitialList(nextRun.analysisRunId)
  }

  function applyFilterPatch(patch: Partial<ExceptionListFilters>) {
    const next = { ...filters, ...patch }
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

  function handleWorkStatusTabSelect(value: WorkStatusTabValue) {
    applyFilterPatch({ workStatus: value === null ? [] : [value], page: 0 })
  }

  function handleSortChange(sortBy: ExceptionSortKey, sortDirection: SortDirection) {
    applyFilterPatch({ sortBy, sortDirection, page: 0 })
  }

  function handlePageChange(nextPage: number) {
    applyFilterPatch({ page: nextPage })
  }

  // Tile clicks reset every other filter first, then apply only that tile's own
  // workStatus/severity/sort, per spec section 7.1.
  function tileFilters(patch: Partial<ExceptionListFilters>): ExceptionListFilters {
    return { ...DEFAULT_FILTERS, workStatus: [], sortBy: 'WORK_PRIORITY', sortDirection: null, ...patch, page: 0 }
  }

  function handleSelectAllTile() {
    const next = tileFilters({})
    setFilters(next)
    setSelectedMetricId(null)
    if (run) fetchList(run.analysisRunId, next)
  }

  function handleSelectCriticalTile() {
    const next = tileFilters({ severity: ['CRITICAL'] })
    setFilters(next)
    setSelectedMetricId(null)
    if (run) fetchList(run.analysisRunId, next)
  }

  function handleSelectDecisionRequiredTile() {
    const next = tileFilters({ workStatus: ['DECISION_REQUIRED'] })
    setFilters(next)
    setSelectedMetricId(null)
    if (run) fetchList(run.analysisRunId, next)
  }

  function handleSelectReviewInputTile() {
    const next = tileFilters({ workStatus: ['REVIEW_INPUT'] })
    setFilters(next)
    setSelectedMetricId(null)
    if (run) fetchList(run.analysisRunId, next)
  }

  function handleSelectBySalesExposureTile() {
    const next = tileFilters({ sortBy: 'SALES_EXPOSURE', sortDirection: 'DESC' })
    setFilters(next)
    setSelectedMetricId(null)
    if (run) fetchList(run.analysisRunId, next)
  }

  function retryList() {
    if (run) {
      fetchList(run.analysisRunId, filters)
    }
  }

  // Spec section 7.6: after a decision save, refresh the current run/filter/sort/page. If the
  // current page becomes empty, step back to the previous valid page once (never an infinite
  // retry). If the selected metric fell out of the tab filter, close the detail panel instead.
  async function handleDecisionSaved() {
    if (!run) return
    listAbortRef.current?.abort()
    const controller = new AbortController()
    listAbortRef.current = controller
    try {
      let result = await api.listExceptions(run.analysisRunId, filters, controller.signal)
      if (controller.signal.aborted) return
      if (result.items.length === 0 && filters.page > 0) {
        const steppedBack = { ...filters, page: filters.page - 1 }
        setFilters(steppedBack)
        result = await api.listExceptions(run.analysisRunId, steppedBack, controller.signal)
        if (controller.signal.aborted) return
      }
      setPage(result)
      if (selectedMetricId !== null && !result.items.some((item) => item.inventoryMetricId === selectedMetricId)) {
        setSelectedMetricId(null)
        setListStatusMessage('처리 결과가 반영되었습니다')
      }
    } catch (e) {
      if (api.isAbortError(e)) return
      setListError(e as ApiError)
    }
  }

  const selectedListItem = page?.items.find((item) => item.inventoryMetricId === selectedMetricId) ?? null

  return (
    <div className="app">
      <p className="app__banner">데모 데이터와 가정 정책으로 계산한 결과입니다. 실제 기업의 재고·이동 정책이 아닙니다.</p>
      <h1>StockPilot 매장간 재고 이동</h1>

      <AnalysisContext onRunStarting={handleRunStarting} onRunCompleted={handleRunCompleted} />

      {run && run.status === 'COMPLETED' && (
        <>
          {page && (
            <WorkQueueSummary
              summary={page.summary}
              onSelectAll={handleSelectAllTile}
              onSelectCritical={handleSelectCriticalTile}
              onSelectDecisionRequired={handleSelectDecisionRequiredTile}
              onSelectReviewInput={handleSelectReviewInputTile}
              onSelectBySalesExposure={handleSelectBySalesExposureTile}
            />
          )}
          {page && (
            <WorkStatusTabs
              summary={page.summary}
              active={(filters.workStatus[0] ?? null) as WorkStatusTabValue}
              onSelect={handleWorkStatusTabSelect}
            />
          )}
          <ExceptionFilters filters={filters} onApply={applyFilterPatch} onReset={handleResetFilters} />
          {listStatusMessage && (
            <p role="status" aria-live="polite">
              {listStatusMessage}
            </p>
          )}
          {listLoading && (
            <p role="status" aria-live="polite">
              목록을 불러오는 중입니다…
            </p>
          )}
          {listError && <ProblemAlert error={listError} onRetry={listError.retryable ? retryList : undefined} />}

          <div className={`workbench${selectedMetricId !== null ? ' workbench--split' : ''}`}>
            {page && (
              <div className="workbench__list">
                <ExceptionList
                  page={page}
                  sortBy={filters.sortBy}
                  sortDirection={filters.sortDirection}
                  onSortChange={handleSortChange}
                  onSelectMetric={setSelectedMetricId}
                  onPageChange={handlePageChange}
                  onResetFilters={handleResetFilters}
                />
              </div>
            )}
            {selectedMetricId !== null && (
              <div className="workbench__detail">
                <ExceptionDetail
                  inventoryMetricId={selectedMetricId}
                  workStatus={selectedListItem?.workStatus ?? null}
                  onClose={() => setSelectedMetricId(null)}
                  actorLabel={actorLabel}
                  onActorLabelChange={persistActorLabel}
                  onDecisionSaved={handleDecisionSaved}
                />
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}

export default App

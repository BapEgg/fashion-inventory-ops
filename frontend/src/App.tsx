import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, getExceptionDetail, isAbortError, listExceptions, runAnalysis } from './api'
import ExceptionDetail from './components/ExceptionDetail'
import ExceptionList from './components/ExceptionList'
import type { InventoryExceptionDetail, InventoryExceptionSummary } from './types'

const DEFAULT_ANALYSIS_DATE = '2026-08-26'

export default function App() {
  const [analysisDate, setAnalysisDate] = useState(DEFAULT_ANALYSIS_DATE)
  const [exceptions, setExceptions] = useState<InventoryExceptionSummary[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<InventoryExceptionDetail | null>(null)

  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const [analysisRunning, setAnalysisRunning] = useState(false)
  const [analysisMessage, setAnalysisMessage] = useState<string | null>(null)

  // Guard against stale async responses: a request superseded by a newer one (the
  // date changed again, or another item was selected) is aborted, and its eventual
  // rejection is ignored rather than overwriting state with out-of-date data.
  const listRequestRef = useRef<AbortController | null>(null)
  const detailRequestRef = useRef<AbortController | null>(null)

  // Lets handleRunAnalysis, after its await, tell whether the user has since selected
  // a different date (a plain closure over `analysisDate` would only ever see the
  // value from the click that started it).
  const analysisDateRef = useRef(analysisDate)
  useEffect(() => {
    analysisDateRef.current = analysisDate
  }, [analysisDate])

  const loadExceptions = useCallback(async (date: string) => {
    listRequestRef.current?.abort()
    const controller = new AbortController()
    listRequestRef.current = controller
    setListLoading(true)
    setListError(null)
    try {
      const result = await listExceptions(date, controller.signal)
      if (listRequestRef.current !== controller) return
      setExceptions(result)
    } catch (error) {
      if (isAbortError(error)) return
      setExceptions([])
      setListError(error instanceof ApiError ? error.message : '예외 목록을 불러오지 못했습니다.')
    } finally {
      if (listRequestRef.current === controller) {
        setListLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    // The previously selected exception belongs to the date being replaced; clear it
    // (and cancel any detail fetch still in flight) so a stale-date screen is never shown.
    detailRequestRef.current?.abort()
    setSelectedId(null)
    setDetail(null)
    loadExceptions(analysisDate)
  }, [analysisDate, loadExceptions])

  async function handleRunAnalysis() {
    const requestedDate = analysisDate
    setAnalysisRunning(true)
    setAnalysisMessage(null)
    try {
      const result = await runAnalysis(requestedDate)
      setAnalysisMessage(
        result.alreadyCompleted
          ? `${result.analysisDate} 분석은 이미 완료되어 있습니다.`
          : `${result.analysisDate} 분석을 완료했습니다.`,
      )
      // Only refresh the list for the date just analyzed if it is still selected.
      // If the user switched dates while this was in flight, the analysisDate-change
      // effect already loaded the newly selected date's list; reloading here with
      // the stale `requestedDate` would incorrectly overwrite it.
      if (analysisDateRef.current === requestedDate) {
        await loadExceptions(requestedDate)
      }
    } catch (error) {
      setAnalysisMessage(error instanceof ApiError ? error.message : '분석 실행에 실패했습니다.')
    } finally {
      setAnalysisRunning(false)
    }
  }

  const loadDetail = useCallback(async (inventoryMetricId: number) => {
    detailRequestRef.current?.abort()
    const controller = new AbortController()
    detailRequestRef.current = controller
    setListError(null)
    try {
      const result = await getExceptionDetail(inventoryMetricId, controller.signal)
      if (detailRequestRef.current !== controller) return
      setDetail(result)
      setSelectedId(inventoryMetricId)
    } catch (error) {
      if (isAbortError(error)) return
      setListError(error instanceof ApiError ? error.message : '상세 정보를 불러오지 못했습니다.')
    }
  }, [])

  function handleBackToList() {
    detailRequestRef.current?.abort()
    setSelectedId(null)
    setDetail(null)
    loadExceptions(analysisDate)
  }

  function handleRefreshDetail() {
    if (selectedId !== null) {
      loadDetail(selectedId)
    }
  }

  return (
    <main className="shell">
      <header className="app-header">
        <h1 id="page-title">StockPilot</h1>
        <p className="app-subtitle">
          먼저 확인할 재고 문제와 매장 간 이동 대안을 빠르게 찾는 업무용 시스템
          <span className="data-note">
            {' '}· SYNTHETIC 데이터 · ASSUMPTION 데모 정책 · 실제 F&amp;F 정책 또는 검증된 산업 표준 아님
          </span>
        </p>
      </header>

      <section className="panel" aria-labelledby="analysis-title">
        <h2 id="analysis-title">분석 실행</h2>
        <div className="form-row">
          <label>
            분석 기준일
            <input
              type="date"
              value={analysisDate}
              disabled={analysisRunning}
              onChange={(event) => setAnalysisDate(event.target.value)}
            />
          </label>
          <button type="button" onClick={handleRunAnalysis} disabled={analysisRunning}>
            {analysisRunning ? '실행 중…' : '분석 실행'}
          </button>
        </div>
        {analysisMessage && <p className="notice">{analysisMessage}</p>}
      </section>

      <section className="panel" aria-labelledby="content-title">
        <h2 id="content-title">{selectedId === null ? '재고 예외 목록' : '재고 예외 상세'}</h2>
        {listError && <p className="error-text">{listError}</p>}
        {selectedId === null ? (
          listLoading ? (
            <p className="notice">불러오는 중…</p>
          ) : (
            <ExceptionList exceptions={exceptions} onSelect={loadDetail} />
          )
        ) : (
          detail && <ExceptionDetail detail={detail} onBack={handleBackToList} onRefresh={handleRefreshDetail} />
        )}
      </section>
    </main>
  )
}

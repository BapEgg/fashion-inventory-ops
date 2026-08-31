import type {
  AnalysisRunRequestBody,
  AnalysisRunResponse,
  AnalysisRunStatusResponse,
  ApiFieldError,
  ApiProblem,
  ExceptionListFilters,
  Mvp2DecisionHistoryResponse,
  Mvp2DecisionRequestBody,
  Mvp2InventoryExceptionDetail,
  Mvp2InventoryExceptionPage,
  Mvp2RebalanceDecisionResponse,
  Mvp2RebalanceSimulationRequestBody,
  Mvp2RebalanceSimulationResponse,
} from './types'

const BASE = '/api'

const STATUS_FALLBACK_MESSAGE: Record<number, string> = {
  400: '요청 형식이 올바르지 않습니다.',
  404: '요청한 대상을 찾을 수 없습니다.',
  409: '현재 상태와 충돌하는 요청입니다.',
  500: '서버에서 예기치 못한 오류가 발생했습니다.',
  503: '일시적으로 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.',
}

/**
 * Every field this application actually reads from a ProblemDetail body, per the React wiring
 * spec section 2.1. Never assumes the server returned this shape -- {@link parseProblem} always
 * validates it first.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly title: string
  readonly detail: string
  readonly retryable: boolean
  readonly requestId: string
  readonly fieldErrors: ApiFieldError[] | undefined

  constructor(problem: ApiProblem) {
    super(problem.detail)
    this.name = 'ApiError'
    this.status = problem.status
    this.code = problem.code
    this.title = problem.title
    this.detail = problem.detail
    this.retryable = problem.retryable
    this.requestId = problem.requestId
    this.fieldErrors = problem.fieldErrors
  }
}

function isApiFieldError(value: unknown): value is ApiFieldError {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const v = value as Record<string, unknown>
  return typeof v.field === 'string' && typeof v.code === 'string' && typeof v.message === 'string'
}

/** Guards against malformed/non-JSON error bodies -- never renders raw HTML or stack text. */
function isApiProblem(value: unknown): value is ApiProblem {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const v = value as Record<string, unknown>
  const shapeOk =
    typeof v.type === 'string' &&
    typeof v.title === 'string' &&
    typeof v.status === 'number' &&
    typeof v.detail === 'string' &&
    typeof v.instance === 'string' &&
    typeof v.code === 'string' &&
    typeof v.retryable === 'boolean' &&
    typeof v.requestId === 'string' &&
    typeof v.timestamp === 'string'
  if (!shapeOk) {
    return false
  }
  if (v.fieldErrors !== undefined && (!Array.isArray(v.fieldErrors) || !v.fieldErrors.every(isApiFieldError))) {
    return false
  }
  return true
}

function fallbackProblem(status: number, instance: string): ApiProblem {
  return {
    type: 'about:blank',
    title: '요청 처리 중 오류가 발생했습니다.',
    status,
    detail: STATUS_FALLBACK_MESSAGE[status] ?? `요청이 실패했습니다 (상태 코드 ${status}).`,
    instance,
    code: 'UNKNOWN_ERROR',
    retryable: status === 503,
    requestId: '',
    timestamp: new Date().toISOString(),
  }
}

async function parseProblem(response: Response, path: string): Promise<ApiProblem> {
  try {
    const parsed: unknown = await response.json()
    return isApiProblem(parsed) ? parsed : fallbackProblem(response.status, path)
  } catch {
    return fallbackProblem(response.status, path)
  }
}

/**
 * A synthesized retryable `ApiError` for a `fetch` call that never reached a `Response` at all
 * (DNS/connection failure, offline, CORS, etc.). Exported so callers that build their own request
 * (`decide`'s idempotent submit) can reuse the exact same shape instead of rendering a raw
 * `TypeError`'s blank `title`/`detail`.
 */
export function toNetworkError(cause: unknown): ApiError {
  if (cause instanceof ApiError) {
    return cause
  }
  return new ApiError({
    type: 'about:blank',
    title: '네트워크 오류',
    status: 0,
    detail: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
    instance: '',
    code: 'NETWORK_ERROR',
    retryable: true,
    requestId: '',
    timestamp: new Date().toISOString(),
  })
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })
  } catch (cause) {
    if (isAbortError(cause)) {
      throw cause
    }
    throw toNetworkError(cause)
  }
  if (!response.ok) {
    throw new ApiError(await parseProblem(response, path))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * `AbortController`-driven cancellation, never surfaced as an error to the user. A real aborted
 * `fetch` rejects with a `DOMException` -- which, per spec, does NOT extend `Error` -- so this
 * checks `.name` directly instead of gating on `instanceof Error`.
 */
export function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: unknown }).name === 'AbortError'
}

// ---------------------------------------------------------------------------
// Analysis
// ---------------------------------------------------------------------------

export function runAnalysis(body: AnalysisRunRequestBody, signal?: AbortSignal): Promise<AnalysisRunResponse> {
  return request('/analyses', { method: 'POST', body: JSON.stringify(body), signal })
}

export function getAnalysisStatus(analysisRunId: number, signal?: AbortSignal): Promise<AnalysisRunStatusResponse> {
  return request(`/analyses/${analysisRunId}`, { signal })
}

// ---------------------------------------------------------------------------
// Inventory-exception queue (run-bound, repeated query keys, never comma-joined)
// ---------------------------------------------------------------------------

function buildExceptionListQuery(analysisRunId: number, filters: ExceptionListFilters): string {
  const params = new URLSearchParams()
  params.set('analysisRunId', String(analysisRunId))
  for (const value of filters.exceptionType) {
    params.append('exceptionType', value)
  }
  for (const value of filters.severity) {
    params.append('severity', value)
  }
  for (const value of filters.signal) {
    params.append('signal', value)
  }
  for (const value of filters.confidence) {
    params.append('confidence', value)
  }
  for (const value of filters.qualityFlag) {
    params.append('qualityFlag', value)
  }
  if (filters.storeId) {
    params.set('storeId', filters.storeId)
  }
  if (filters.skuId) {
    params.set('skuId', filters.skuId)
  }
  if (filters.hasExecutableCandidate !== null) {
    params.set('hasExecutableCandidate', String(filters.hasExecutableCandidate))
  }
  for (const value of filters.workStatus) {
    params.append('workStatus', value)
  }
  params.set('sortBy', filters.sortBy)
  if (filters.sortDirection !== null) {
    params.set('sortDirection', filters.sortDirection)
  }
  params.set('page', String(filters.page))
  params.set('size', String(filters.size))
  return params.toString()
}

export function listExceptions(
  analysisRunId: number,
  filters: ExceptionListFilters,
  signal?: AbortSignal,
): Promise<Mvp2InventoryExceptionPage> {
  return request(`/inventory-exceptions?${buildExceptionListQuery(analysisRunId, filters)}`, { signal })
}

export function getExceptionDetail(
  inventoryMetricId: number,
  signal?: AbortSignal,
): Promise<Mvp2InventoryExceptionDetail> {
  return request(`/inventory-exceptions/${inventoryMetricId}`, { signal })
}

// ---------------------------------------------------------------------------
// MANUAL quantity-test simulation (always the complete MVP-2 version tuple)
// ---------------------------------------------------------------------------

export function simulateManualQuantity(
  body: Mvp2RebalanceSimulationRequestBody,
  signal?: AbortSignal,
): Promise<Mvp2RebalanceSimulationResponse> {
  return request('/rebalancing-simulations', { method: 'POST', body: JSON.stringify(body), signal })
}

// ---------------------------------------------------------------------------
// Decision (idempotent) and canonical history
// ---------------------------------------------------------------------------

export function decide(
  body: Mvp2DecisionRequestBody,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<Mvp2RebalanceDecisionResponse> {
  return request('/rebalancing-decisions', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(body),
    signal,
  })
}

export function getDecisionHistory(
  recommendationId: number,
  signal?: AbortSignal,
): Promise<Mvp2DecisionHistoryResponse> {
  return request(`/rebalancing-decisions/${recommendationId}`, { signal })
}

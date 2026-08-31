import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  decide,
  getAnalysisStatus,
  getDecisionHistory,
  getExceptionDetail,
  isAbortError,
  listExceptions,
  runAnalysis,
  simulateManualQuantity,
  toNetworkError,
} from './api'
import type { ExceptionListFilters, Mvp2DecisionRequestBody, Mvp2RebalanceSimulationRequestBody } from './types'

function jsonResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response
}

function rawResponse(status: number, text: string) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError(`Unexpected token in ${text}`)
    },
  } as unknown as Response
}

const DEFAULT_FILTERS: ExceptionListFilters = {
  exceptionType: [],
  severity: [],
  signal: [],
  confidence: [],
  qualityFlag: [],
  storeId: null,
  skuId: null,
  hasExecutableCandidate: null,
  workStatus: [],
  sortBy: 'WORK_PRIORITY',
  sortDirection: null,
  page: 0,
  size: 20,
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('runAnalysis', () => {
  it('POSTs exactly {analysisDate, inputSnapshotVersion} -- never ruleVersion', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { analysisRunId: 1, status: 'RUNNING' }))
    vi.stubGlobal('fetch', fetchMock)

    await runAnalysis({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/analyses')
    expect(init.method).toBe('POST')
    const sentBody = JSON.parse(init.body)
    expect(sentBody).toEqual({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })
    expect(sentBody.ruleVersion).toBeUndefined()
  })
})

describe('getAnalysisStatus', () => {
  it('maps the status GET response through', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(200, {
        analysisRunId: 7,
        analysisDate: '2026-09-30',
        inputSnapshotVersion: 'V1',
        ruleVersion: 'R1',
        status: 'COMPLETED',
        startedAt: '2026-09-30T00:00:00Z',
        completedAt: '2026-09-30T00:05:00Z',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await getAnalysisStatus(7)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/analyses/7')
    expect(result.status).toBe('COMPLETED')
    expect(result.analysisRunId).toBe(7)
  })
})

describe('listExceptions query building', () => {
  it('sends array filters as repeated keys and scalars/page exactly once', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { items: [], page: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    const filters: ExceptionListFilters = {
      ...DEFAULT_FILTERS,
      exceptionType: ['STOCKOUT_RISK', 'OVERSTOCK'],
      severity: ['CRITICAL'],
      storeId: 'ST-1',
      hasExecutableCandidate: true,
      workStatus: ['DECISION_REQUIRED', 'ON_HOLD'],
      sortBy: 'SALES_EXPOSURE',
      sortDirection: 'DESC',
      page: 2,
      size: 50,
    }

    await listExceptions(9, filters)

    const url = fetchMock.mock.calls[0][0] as string
    const query = new URLSearchParams(url.split('?')[1])
    expect(query.getAll('exceptionType')).toEqual(['STOCKOUT_RISK', 'OVERSTOCK'])
    expect(query.getAll('severity')).toEqual(['CRITICAL'])
    expect(query.get('storeId')).toBe('ST-1')
    expect(query.get('hasExecutableCandidate')).toBe('true')
    expect(query.getAll('workStatus')).toEqual(['DECISION_REQUIRED', 'ON_HOLD'])
    expect(query.get('sortBy')).toBe('SALES_EXPOSURE')
    expect(query.get('sortDirection')).toBe('DESC')
    expect(query.get('analysisRunId')).toBe('9')
    expect(query.get('page')).toBe('2')
    expect(query.get('size')).toBe('50')
  })

  it('omits storeId/skuId/hasExecutableCandidate entirely when unset, never sending empty/null', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { items: [], page: 0 }))
    vi.stubGlobal('fetch', fetchMock)

    await listExceptions(9, DEFAULT_FILTERS)

    const url = fetchMock.mock.calls[0][0] as string
    const query = new URLSearchParams(url.split('?')[1])
    expect(query.has('storeId')).toBe(false)
    expect(query.has('skuId')).toBe(false)
    expect(query.has('hasExecutableCandidate')).toBe(false)
    expect(query.has('workStatus')).toBe(false)
    expect(query.has('sortDirection')).toBe(false)
    expect(query.get('sortBy')).toBe('WORK_PRIORITY')
  })
})

describe('getExceptionDetail', () => {
  it('GETs the metric-id-scoped detail path', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}))
    vi.stubGlobal('fetch', fetchMock)
    await getExceptionDetail(123)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/inventory-exceptions/123')
  })
})

describe('simulateManualQuantity', () => {
  it('sends the complete version tuple plus recommendationId/requestedQuantity', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { feasible: true }))
    vi.stubGlobal('fetch', fetchMock)

    const body: Mvp2RebalanceSimulationRequestBody = {
      analysisRunId: 9,
      inputSnapshotVersion: 'V1',
      ruleVersion: 'R1',
      candidateVersion: 3,
      recommendationId: 55,
      requestedQuantity: 12,
    }
    await simulateManualQuantity(body)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/rebalancing-simulations')
    expect(JSON.parse(init.body)).toEqual(body)
  })
})

describe('decide', () => {
  it('sends exactly one Idempotency-Key header with the given key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { created: true, decisionSequence: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    const body: Mvp2DecisionRequestBody = {
      analysisRunId: 9,
      inputSnapshotVersion: 'V1',
      ruleVersion: 'R1',
      candidateVersion: 3,
      recommendationId: 55,
      decisionStatus: 'APPROVED',
      selectedQuantity: 12,
      policyException: false,
      reasonCode: null,
      reason: null,
      actorLabel: 'tester',
    }
    await decide(body, 'key-abc-123')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/rebalancing-decisions')
    expect(init.headers['Idempotency-Key']).toBe('key-abc-123')
    expect(Object.keys(init.headers).filter((k) => k.toLowerCase() === 'idempotency-key')).toHaveLength(1)
    expect(JSON.parse(init.body)).toEqual(body)
  })
})

describe('getDecisionHistory', () => {
  it('GETs the canonical history path for a recommendation', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { decisions: [] }))
    vi.stubGlobal('fetch', fetchMock)
    await getDecisionHistory(55)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/rebalancing-decisions/55')
  })
})

describe('RFC 9457 error parsing', () => {
  it('throws an ApiError carrying the well-formed ProblemDetail fields', async () => {
    const problem = {
      type: 'about:blank',
      title: '요청 형식 오류',
      status: 400,
      detail: '필수 항목이 누락되었습니다.',
      instance: '/api/analyses',
      code: 'VALIDATION_ERROR',
      retryable: false,
      requestId: 'req-1',
      timestamp: '2026-08-29T00:00:00Z',
      fieldErrors: [{ field: 'analysisDate', code: 'REQUIRED', message: '필수입니다.' }],
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(400, problem)))

    await expect(runAnalysis({ analysisDate: '', inputSnapshotVersion: '' })).rejects.toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      retryable: false,
      requestId: 'req-1',
    })
  })

  it('falls back to a status-keyed Korean message for a malformed/non-JSON body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(rawResponse(500, '<html>Internal Server Error</html>')))

    try {
      await runAnalysis({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })
      throw new Error('expected runAnalysis to reject')
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError)
      const apiError = e as ApiError
      expect(apiError.status).toBe(500)
      expect(apiError.detail).toBe('서버에서 예기치 못한 오류가 발생했습니다.')
      expect(apiError.code).toBe('UNKNOWN_ERROR')
    }
  })

  it('falls back when the body is JSON but not a well-formed ProblemDetail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(404, { message: 'not found' })))

    await expect(runAnalysis({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })).rejects.toMatchObject({
      status: 404,
      code: 'UNKNOWN_ERROR',
      detail: '요청한 대상을 찾을 수 없습니다.',
    })
  })

  it('marks a fallback 503 as retryable and others as not', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(rawResponse(503, 'unavailable')))
    await expect(runAnalysis({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })).rejects.toMatchObject({
      retryable: true,
    })
  })
})

describe('isAbortError', () => {
  it('recognizes a DOMException/Error named AbortError', () => {
    const err = new DOMException('aborted', 'AbortError')
    expect(isAbortError(err)).toBe(true)
  })

  it('does not treat an ApiError or a plain error as an abort', () => {
    expect(isAbortError(new Error('network down'))).toBe(false)
    expect(
      isAbortError(
        new ApiError({
          type: 'about:blank',
          title: 't',
          status: 500,
          detail: 'd',
          instance: 'i',
          code: 'X',
          retryable: false,
          requestId: '',
          timestamp: '',
        }),
      ),
    ).toBe(false)
  })
})

describe('network failure normalization', () => {
  it('normalizes a raw fetch rejection into a populated retryable ApiError, not a blank cast TypeError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    try {
      await runAnalysis({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })
      throw new Error('expected runAnalysis to reject')
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError)
      const apiError = e as ApiError
      expect(apiError.title).toBe('네트워크 오류')
      expect(apiError.detail.length).toBeGreaterThan(0)
      expect(apiError.retryable).toBe(true)
      expect(apiError.code).toBe('NETWORK_ERROR')
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('applies the same normalization to every API call, not just analyses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    try {
      await expect(getDecisionHistory(55)).rejects.toMatchObject({ title: '네트워크 오류', retryable: true })
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('passes a real abort rejection through unchanged, so isAbortError still recognizes it', async () => {
    const abortError = new DOMException('aborted', 'AbortError')
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortError))
    try {
      await runAnalysis({ analysisDate: '2026-09-30', inputSnapshotVersion: 'V1' })
      throw new Error('expected runAnalysis to reject')
    } catch (e) {
      expect(isAbortError(e)).toBe(true)
      expect(e).toBe(abortError)
    } finally {
      vi.unstubAllGlobals()
    }
  })
})

describe('toNetworkError', () => {
  it('returns an existing ApiError unchanged', () => {
    const original = new ApiError({
      type: 'about:blank',
      title: 't',
      status: 500,
      detail: 'd',
      instance: 'i',
      code: 'X',
      retryable: false,
      requestId: '',
      timestamp: '',
    })
    expect(toNetworkError(original)).toBe(original)
  })

  it('wraps a non-ApiError cause into a populated retryable network ApiError', () => {
    const wrapped = toNetworkError(new TypeError('Failed to fetch'))
    expect(wrapped).toBeInstanceOf(ApiError)
    expect(wrapped.retryable).toBe(true)
    expect(wrapped.title.length).toBeGreaterThan(0)
    expect(wrapped.detail.length).toBeGreaterThan(0)
  })
})

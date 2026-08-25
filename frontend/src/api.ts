import type {
  AnalysisRunResponse,
  DecisionStatus,
  InventoryExceptionDetail,
  InventoryExceptionSummary,
  RebalanceDecisionResponse,
  RebalanceSimulationResponse,
} from './types'

const BASE = '/api'

export class ApiError extends Error {}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message = body?.detail ?? body?.message ?? `요청이 실패했습니다 (${response.status})`
    throw new ApiError(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function runAnalysis(analysisDate: string): Promise<AnalysisRunResponse> {
  return request('/analyses', {
    method: 'POST',
    body: JSON.stringify({ analysisDate }),
  })
}

export function listExceptions(analysisDate?: string, signal?: AbortSignal): Promise<InventoryExceptionSummary[]> {
  const query = analysisDate ? `?analysisDate=${encodeURIComponent(analysisDate)}` : ''
  return request(`/inventory-exceptions${query}`, { signal })
}

export function getExceptionDetail(inventoryMetricId: number, signal?: AbortSignal): Promise<InventoryExceptionDetail> {
  return request(`/inventory-exceptions/${inventoryMetricId}`, { signal })
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError'
}

export function simulateRebalance(
  recommendationId: number,
  requestedQuantity: number,
): Promise<RebalanceSimulationResponse> {
  return request('/rebalancing-simulations', {
    method: 'POST',
    body: JSON.stringify({ recommendationId, requestedQuantity }),
  })
}

export function decideRebalance(input: {
  recommendationId: number
  decisionStatus: DecisionStatus
  selectedQuantity: number
  reason: string
  actorLabel: string
}): Promise<RebalanceDecisionResponse> {
  return request('/rebalancing-decisions', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

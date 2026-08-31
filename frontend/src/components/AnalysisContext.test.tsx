import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { AnalysisContext } from './AnalysisContext'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof api>()
  return { ...actual, runAnalysis: vi.fn(), getAnalysisStatus: vi.fn() }
})

function baseRun(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    analysisRunId: 1,
    analysisDate: '2026-09-30',
    ruleVersion: 'R1',
    status: 'RUNNING',
    alreadyCompleted: false,
    inputSnapshotVersion: 'V1',
    startedAt: '2026-09-30T00:00:00Z',
    completedAt: null,
    ...overrides,
  }
}

/** Flushes pending microtasks (mocked API promises) without relying on real wall-clock time. */
async function flush() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0)
  })
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('AnalysisContext', () => {
  it('calls onRunStarting synchronously on launch, before the POST resolves', () => {
    let resolveRun!: (value: Awaited<ReturnType<typeof api.runAnalysis>>) => void
    vi.mocked(api.runAnalysis).mockReturnValue(new Promise((resolve) => (resolveRun = resolve)))
    const onRunStarting = vi.fn()
    render(<AnalysisContext onRunStarting={onRunStarting} onRunCompleted={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '재고 현황 갱신' }))

    expect(onRunStarting).toHaveBeenCalledTimes(1)
    resolveRun(baseRun({ status: 'RUNNING' }) as any)
  })

  it('reports an immediately COMPLETED (replay) run without polling', async () => {
    vi.mocked(api.runAnalysis).mockResolvedValue(
      baseRun({ status: 'COMPLETED', alreadyCompleted: true, completedAt: '2026-09-30T00:05:00Z' }) as any,
    )
    const onRunCompleted = vi.fn()
    render(<AnalysisContext onRunStarting={vi.fn()} onRunCompleted={onRunCompleted} />)

    fireEvent.click(screen.getByRole('button', { name: '재고 현황 갱신' }))
    await flush()

    expect(screen.getByText('기존 갱신 결과를 불러왔습니다.')).toBeInTheDocument()
    // The run-context status renders the exhaustive Korean label, never the raw enum code.
    expect(screen.getByText('갱신 완료')).toBeInTheDocument()
    expect(screen.queryByText('COMPLETED')).not.toBeInTheDocument()
    expect(onRunCompleted).toHaveBeenCalledTimes(1)
    expect(onRunCompleted).toHaveBeenCalledWith(expect.objectContaining({ analysisRunId: 1, status: 'COMPLETED' }), true)
    expect(api.getAnalysisStatus).not.toHaveBeenCalled()
  })

  it('polls while RUNNING and reports COMPLETED once the poll observes it', async () => {
    vi.mocked(api.runAnalysis).mockResolvedValue(baseRun({ status: 'RUNNING' }) as any)
    vi.mocked(api.getAnalysisStatus)
      .mockResolvedValueOnce(baseRun({ status: 'RUNNING' }) as any)
      .mockResolvedValueOnce(baseRun({ status: 'COMPLETED', completedAt: '2026-09-30T00:05:00Z' }) as any)
    const onRunCompleted = vi.fn()
    render(<AnalysisContext onRunStarting={vi.fn()} onRunCompleted={onRunCompleted} />)

    fireEvent.click(screen.getByRole('button', { name: '재고 현황 갱신' }))
    await flush()
    expect(screen.getByText('재고 현황을 갱신하고 있습니다…')).toBeInTheDocument()
    expect(api.runAnalysis).toHaveBeenCalledTimes(1)

    await advance(1500)
    expect(api.getAnalysisStatus).toHaveBeenCalledTimes(1)
    expect(screen.getByText('재고 현황을 갱신하고 있습니다…')).toBeInTheDocument()

    await advance(1500)

    expect(screen.getByText('재고 현황 갱신이 완료되었습니다.')).toBeInTheDocument()
    expect(onRunCompleted).toHaveBeenCalledWith(expect.objectContaining({ status: 'COMPLETED' }), false)
  })

  it('shows a failure state when the run resolves to FAILED during polling', async () => {
    vi.mocked(api.runAnalysis).mockResolvedValue(baseRun({ status: 'RUNNING' }) as any)
    vi.mocked(api.getAnalysisStatus).mockResolvedValueOnce(baseRun({ status: 'FAILED' }) as any)
    render(<AnalysisContext onRunStarting={vi.fn()} onRunCompleted={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '재고 현황 갱신' }))
    await flush()
    await advance(1500)

    expect(screen.getByText('재고 현황 갱신에 실패했습니다.')).toBeInTheDocument()
  })

  it('switches to a timeout state after the maximum poll attempts without a manual refresh call', async () => {
    vi.mocked(api.runAnalysis).mockResolvedValue(baseRun({ status: 'RUNNING' }) as any)
    vi.mocked(api.getAnalysisStatus).mockResolvedValue(baseRun({ status: 'RUNNING' }) as any)
    render(<AnalysisContext onRunStarting={vi.fn()} onRunCompleted={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '재고 현황 갱신' }))
    await flush()

    for (let i = 0; i < 40; i += 1) {
      await advance(1500)
    }

    expect(screen.getByText('갱신이 계속 진행 중입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '상태 확인' })).toBeInTheDocument()
    expect(api.getAnalysisStatus).toHaveBeenCalledTimes(40)
  }, 20000)
})

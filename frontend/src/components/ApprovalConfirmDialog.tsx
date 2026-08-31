import { useEffect, useRef } from 'react'
import { formatCoverageDays, formatQuantity } from '../formatters'

export interface ApprovalConfirmSummary {
  donorStoreName: string
  receiverStoreName: string
  productName: string
  quantity: number
  expectedArrivalDate: string | null
  receiverBeforeAvailable: number
  receiverAfterAvailable: number
  receiverAfterCoverageDays: number | null
  donorBeforeAvailable: number
  donorAfterAvailable: number
  donorAfterCoverageDays: number | null
}

/**
 * The accessible confirmation modal an `이동 승인` click opens before the POST fires, per spec
 * section 9.5. A minimal hand-rolled dialog (no new UI library, per the implementation contract):
 * traps focus, restores it to the triggering button on close, and closes on Escape/backdrop click.
 */
export function ApprovalConfirmDialog({
  summary,
  submitting,
  onCancel,
  onConfirm,
}: {
  summary: ApprovalConfirmSummary
  submitting: boolean
  onCancel: () => void
  onConfirm: () => void
}) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const confirmButtonRef = useRef<HTMLButtonElement | null>(null)
  const previouslyFocused = useRef<Element | null>(null)

  useEffect(() => {
    previouslyFocused.current = document.activeElement
    confirmButtonRef.current?.focus()
    return () => {
      if (previouslyFocused.current instanceof HTMLElement) {
        previouslyFocused.current.focus()
      }
    }
  }, [])

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape') {
      e.stopPropagation()
      onCancel()
      return
    }
    if (e.key !== 'Tab' || !dialogRef.current) {
      return
    }
    const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    )
    if (focusable.length === 0) {
      return
    }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault()
      last.focus()
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="approval-confirm-dialog__backdrop" onMouseDown={onCancel}>
      <div
        ref={dialogRef}
        className="approval-confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="approval-confirm-title"
        onMouseDown={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        <h2 id="approval-confirm-title">이 이동안을 승인할까요?</h2>
        <dl className="approval-confirm-dialog__body">
          <div>
            <dt>이동 경로</dt>
            <dd>
              {summary.donorStoreName} → {summary.receiverStoreName}
            </dd>
          </div>
          <div>
            <dt>상품</dt>
            <dd>{summary.productName}</dd>
          </div>
          <div>
            <dt>이동수량</dt>
            <dd>{formatQuantity(summary.quantity)}개</dd>
          </div>
          <div>
            <dt>예상 도착일</dt>
            <dd>{summary.expectedArrivalDate ?? '—'}</dd>
          </div>
          <div>
            <dt>입고점 예상재고 (전 → 후)</dt>
            <dd>
              {formatQuantity(summary.receiverBeforeAvailable)} → {formatQuantity(summary.receiverAfterAvailable)}
              {' · '}
              {formatCoverageDays(summary.receiverAfterCoverageDays)}
            </dd>
          </div>
          <div>
            <dt>출고점 예상재고 (전 → 후)</dt>
            <dd>
              {formatQuantity(summary.donorBeforeAvailable)} → {formatQuantity(summary.donorAfterAvailable)}
              {' · '}
              {formatCoverageDays(summary.donorAfterCoverageDays)}
            </dd>
          </div>
        </dl>
        <p className="approval-confirm-dialog__notice">
          승인하면 ERP 이동요청 초안이 생성됩니다. 실제 출고 완료는 아닙니다.
        </p>
        <div className="approval-confirm-dialog__actions">
          <button type="button" onClick={onCancel} disabled={submitting}>
            취소
          </button>
          <button type="button" ref={confirmButtonRef} onClick={onConfirm} disabled={submitting} className="btn-primary">
            {submitting ? '승인 중…' : `${formatQuantity(summary.quantity)}개 이동 승인`}
          </button>
        </div>
      </div>
    </div>
  )
}

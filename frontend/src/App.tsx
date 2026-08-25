const plannedFeatures = [
  'Oracle 합성 데이터 적재',
  'Batch 재고 예외 분석',
  '재배분 수량 시뮬레이션',
  '승인·거절 이력 기록',
  '선택적 AI 결과 설명',
]

export default function App() {
  return (
    <main className="shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">FASHION INVENTORY OPERATIONS</p>
        <h1 id="page-title">StockPilot</h1>
        <p className="summary">
          먼저 확인할 재고 문제와 매장 간 이동 대안을 빠르게 찾는 업무용 시스템
        </p>
        <span className="status">프로젝트 구조 준비 완료 · 핵심 기능 구현 전</span>
      </section>

      <section className="panel" aria-labelledby="planned-title">
        <h2 id="planned-title">1~2일 MVP 구현 범위</h2>
        <ul>
          {plannedFeatures.map((feature) => (
            <li key={feature}>{feature}</li>
          ))}
        </ul>
        <p className="notice">
          현재 화면은 실행 구조 확인용입니다. 재고 수치와 업무 규칙은 합성 데이터와
          데모 가정값만 사용합니다.
        </p>
      </section>
    </main>
  )
}

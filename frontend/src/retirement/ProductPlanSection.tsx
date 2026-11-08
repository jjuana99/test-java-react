import { eur, pct, type ProductPlan } from './api'

interface Segment {
  key: string
  label: string
  value: number
  className: string
  note?: string
}

/** Stacked horizontal bar with an always-visible legend (identity never relies on color alone). */
function StackedBar({ segments, total, ariaLabel, unit }: { segments: Segment[]; total: number; ariaLabel: string; unit: string }) {
  const visible = segments.filter((s) => s.value > 0)
  return (
    <div className="stacked">
      <div className="stacked-bar" role="img" aria-label={ariaLabel}>
        {visible.map((s) => (
          <span
            key={s.key}
            className={`seg ${s.className}`}
            style={{ width: `${(s.value / total) * 100}%` }}
            title={`${s.label}: ${eur(s.value)}${unit}`}
          />
        ))}
      </div>
      <ul className="stacked-legend">
        {segments.map((s) => (
          <li key={s.key}>
            <span className={`swatch ${s.className}`} aria-hidden="true" />
            <span className="legend-label">{s.label}</span>
            <strong>
              {eur(s.value)}
              <span className="muted small">{unit}</span>
            </strong>
            <span className="muted small">{total > 0 ? pct((s.value / total) * 100, 0) : ''}</span>
            {s.note && <span className="legend-note muted small">{s.note}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** The retirement plan funded with the recommended VidaCaixa products. */
export default function ProductPlanSection({ plan }: { plan: ProductPlan }) {
  const { income, budget } = plan
  const feeExtra = plan.monthlyContribution - plan.monthlyWithoutFees
  const names = plan.holdings.map((h) => h.name).join(' + ')

  return (
    <section className="card product-plan">
      <div className="card-head">
        <div>
          <span className="hero-kicker">
            {plan.scenario === 'PRUDENCE' ? 'Colchón de prudencia (pensión pública un 20 % menor)' : 'Tu plan con productos'}{' '}
            {plan.provider}
          </span>
          <h3>{names}</h3>
        </div>
        <div className="plan-headline">
          <span className="product-amount">
            {eur(plan.monthlyContribution)}
            <span className="muted small">/mes</span>
          </span>
          {feeExtra > 0.5 && (
            <span className="muted small">
              {eur(plan.monthlyWithoutFees)}/mes sin comisiones (+{eur(feeExtra)} por comisiones)
            </span>
          )}
        </div>
      </div>

      <h4 className="bar-title">Tus ingresos cada año de jubilación</h4>
      <StackedBar
        unit="/año"
        total={income.target}
        ariaLabel="De dónde salen tus ingresos en la jubilación"
        segments={[
          { key: 'pension', label: 'Pensión pública', value: income.publicPension, className: 'seg-pension' },
          {
            key: 'contrib',
            label: 'Lo que tú has ahorrado',
            value: income.fromContributions,
            className: 'seg-savings',
          },
          {
            key: 'returns',
            label: `Rentabilidad de los productos ${plan.provider}`,
            value: income.fromReturns,
            className: 'seg-returns',
            note: 'ya descontadas las comisiones',
          },
        ]}
      />

      <h4 className="bar-title">Cada mes, de tu sueldo neto de hoy</h4>
      <StackedBar
        unit="/mes"
        total={budget.netMonthlyIncome + budget.taxSaving}
        ariaLabel="Reparto de tu sueldo neto mensual"
        segments={[
          {
            key: 'invested',
            label: 'Ahorro que se invierte',
            value: budget.invested,
            className: 'seg-savings',
          },
          {
            key: 'fees',
            label: `Comisiones ${plan.provider} (media)`,
            value: budget.fees,
            className: 'seg-fees',
            note: 'no se cobran aparte: se descuentan del fondo',
          },
          {
            key: 'spend',
            label: 'Te queda para gastar',
            value: budget.leftToSpend,
            className: 'seg-spend',
            note: budget.taxSaving > 0 ? `incluye ${eur(budget.taxSaving)}/mes de ahorro en IRPF` : undefined,
          },
        ]}
      />
      <p className="muted small">
        Sueldo neto estimado: {eur(budget.netMonthlyIncome)}/mes en 12 pagas. Tu aportación de{' '}
        {eur(plan.monthlyContribution)}/mes se reparte entre lo que se invierte y lo que, de media, se llevan las
        comisiones.
      </p>

      <div className="table-wrap">
        <table className="compact">
          <thead>
            <tr>
              <th>Producto</th>
              <th className="num">Aportas</th>
              <th className="num">Rentab. neta*</th>
              <th className="num">Comisión</th>
              <th className="num">Aportado</th>
              <th className="num">Rentabilidad</th>
              <th className="num">Comisiones</th>
              <th className="num">Al jubilarte</th>
            </tr>
          </thead>
          <tbody>
            {plan.holdings.map((h) => (
              <tr key={h.productId}>
                <td>{h.name}</td>
                <td className="num">{eur(h.monthly)}/mes</td>
                <td className="num">{pct(h.netRealReturn * 100, 2)}</td>
                <td className="num">
                  {h.feePct > 0 ? pct(h.feePct, 2) : 'incluida'}
                  {h.feeAssumed && ' (supuesta)'}
                </td>
                <td className="num">{eur(h.contributions)}</td>
                <td className="num">{eur(h.returns)}</td>
                <td className="num">{eur(h.feesPaid)}</td>
                <td className="num">
                  <strong>{eur(h.finalBalance)}</strong>
                </td>
              </tr>
            ))}
            <tr className="total-row">
              <td>Total (con tu ahorro actual)</td>
              <td className="num">{eur(plan.monthlyContribution)}/mes</td>
              <td />
              <td />
              <td className="num">{eur(plan.contributions)}</td>
              <td className="num">{eur(plan.returns)}</td>
              <td className="num">{eur(plan.feesPaid)}</td>
              <td className="num">
                <strong>{eur(plan.capitalAtRetirement)}</strong>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ul className="assumptions small">
        <li>*Rentabilidad anual por encima de la inflación, después de comisiones.</li>
        {plan.assumptions.map((a) => (
          <li key={a}>{a}</li>
        ))}
      </ul>
    </section>
  )
}

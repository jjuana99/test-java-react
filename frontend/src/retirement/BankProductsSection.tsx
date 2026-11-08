import { fmtDate } from '../monitor/api'
import { eur, pct, type BankProducts, type ProductRole, type ProductSuggestion } from './api'

const ROLE_ORDER: ProductRole[] = ['PENSION_PLAN', 'SAVINGS', 'LUMP_SUM', 'PAYOUT']

function Facts({ s }: { s: ProductSuggestion }) {
  return (
    <div className="product-facts small">
      {s.risk != null && (
        <span>
          Riesgo <strong>{s.risk}/{s.riskScale}</strong>
        </span>
      )}
      {s.type !== 'ANNUITY' && (
        <span>
          Comisión anual <strong>{s.totalFee != null ? pct(s.totalFee, 2) : 'no publicada'}</strong>
        </span>
      )}
      {s.return12m != null && (
        <span>
          Rentab. 12 meses <strong>{pct(s.return12m, 2)}</strong>
        </span>
      )}
    </div>
  )
}

function ProductCard({ s }: { s: ProductSuggestion }) {
  return (
    <article className={s.best ? 'product-card best' : 'product-card'}>
      <div className="product-head">
        <div>
          <span className={s.best ? 'product-badge best' : 'product-badge'}>
            {s.best ? '★ Mejor opción para ti' : 'Recomendado'}
          </span>
          <h4>{s.name}</h4>
        </div>
        {s.monthlyAmount != null && s.monthlyAmount > 0 && (
          <span className="product-amount">
            {eur(s.monthlyAmount)}
            <span className="muted small">/mes</span>
          </span>
        )}
      </div>
      <Facts s={s} />
      <ul className="product-list pros">
        {s.reasons.map((r) => (
          <li key={r}>{r}</li>
        ))}
      </ul>
      <ul className="product-list cons">
        {s.cautions.map((c) => (
          <li key={c}>{c}</li>
        ))}
      </ul>
      <a className="small" href={s.url} target="_blank" rel="noreferrer">
        Ver ficha oficial ↗
      </a>
    </article>
  )
}

/** Second-ranked product of a role and a criterion-by-criterion account of why it is not first. */
function RunnerUpCard({ s }: { s: ProductSuggestion }) {
  const why = s.versusBest
  if (!why) return null
  return (
    <article className="product-card runner-up">
      <div className="product-head">
        <div>
          <span className="product-badge">Segunda opción</span>
          <h4>{s.name}</h4>
        </div>
        <div className="score-compare" aria-label={`${s.score} frente a ${why.winnerScore} puntos sobre 100`}>
          <span className="small muted">Encaje</span>
          <span className="score-pair">
            <strong>{s.score}</strong>
            <span className="muted"> / 100</span>
          </span>
          <span className="small muted">
            {why.winnerName}: {why.winnerScore}
          </span>
        </div>
      </div>
      <Facts s={s} />
      <p className="verdict">{why.verdict}</p>
      {why.losses.length > 0 && (
        <>
          <h5>Por qué no es la primera</h5>
          <ul className="product-list losses">
            {why.losses.map((l) => (
              <li key={l}>{l}</li>
            ))}
          </ul>
        </>
      )}
      {why.gains.length > 0 && (
        <>
          <h5>Dónde es mejor</h5>
          <ul className="product-list pros">
            {why.gains.map((g) => (
              <li key={g}>{g}</li>
            ))}
          </ul>
        </>
      )}
      <a className="small" href={s.url} target="_blank" rel="noreferrer">
        Ver ficha oficial ↗
      </a>
    </article>
  )
}

function Alternatives({ items }: { items: ProductSuggestion[] }) {
  if (items.length === 0) return null
  return (
    <details className="alternatives">
      <summary className="small">Comparar con {items.length} alternativa(s)</summary>
      <div className="table-wrap">
        <table className="compact">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Encaje</th>
              <th className="num">Riesgo</th>
              <th className="num">Comisión</th>
              <th className="num">Coste comisiones*</th>
            </tr>
          </thead>
          <tbody>
            {items.map((s) => (
              <tr key={s.productId} title={s.versusBest?.verdict}>
                <td>
                  <a href={s.url} target="_blank" rel="noreferrer">
                    {s.name}
                  </a>
                </td>
                <td>
                  <span className="score" aria-label={`Encaje ${s.score} sobre 100`}>
                    <span style={{ width: `${s.score}%` }} />
                  </span>
                </td>
                <td className="num">{s.risk != null ? `${s.risk}/${s.riskScale}` : '—'}</td>
                <td className="num">{s.totalFee != null ? pct(s.totalFee, 2) : 'n/d'}</td>
                <td className="num">{s.feeCostAtRetirement != null ? eur(s.feeCostAtRetirement) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}

/** CaixaBank products matched to the plan, one recommended product per role plus alternatives. */
export default function BankProductsSection({ products }: { products: BankProducts }) {
  const roles = ROLE_ORDER.filter((r) => products.suggestions.some((s) => s.role === r))
  return (
    <section className="card bank-products">
      <div className="card-head">
        <div>
          <h3>Productos de {products.provider} para tu plan</h3>
          <p className="product-summary">{products.summary}</p>
        </div>
      </div>

      {roles.map((role) => {
        const items = products.suggestions.filter((s) => s.role === role)
        const top = items.find((s) => s.recommended) ?? items[0]
        const runnerUp = items.find((s) => s.rank === 2)
        return (
          <div key={role} className="product-role">
            <h4 className="role-title">{top.roleLabel}</h4>
            <ProductCard s={top} />
            {runnerUp && <RunnerUpCard s={runnerUp} />}
            <Alternatives items={items.filter((s) => s !== top && s !== runnerUp)} />
          </div>
        )
      })}

      <div className="disclaimer small">
        <p>
          Información pública de caixabank.es y vidacaixa.es revisada el {fmtDate(products.verifiedOn)}. {products.note}
        </p>
        <p>
          *Coste de comisiones: capital que dejarías de tener al jubilarte frente al mismo ahorro sin comisiones, con
          la rentabilidad supuesta del plan.
        </p>
        {products.unavailable.map((u) => (
          <p key={u.name}>
            No incluido: <strong>{u.name}</strong>. {u.reason}
          </p>
        ))}
        <p>
          Selección automática según reglas transparentes; no es asesoramiento financiero ni una recomendación de
          CaixaBank. Antes de contratar, la entidad debe evaluar tu idoneidad y entregarte el documento de datos
          fundamentales de cada producto.
        </p>
      </div>
    </section>
  )
}

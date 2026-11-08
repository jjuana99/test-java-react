import LineChart from '../monitor/LineChart'
import BankProductsSection from './BankProductsSection'
import NeighbourhoodSection from './NeighbourhoodSection'
import ProductPlanSection from './ProductPlanSection'
import { fmtDate } from '../monitor/api'
import { eur, pct, profileLabel, type Neighbourhood, type RetirementPlan, type RiskProfile } from './api'

const RECOMMENDATION_ICON: Record<string, string> = {
  PENSION_PLAN: '🏦',
  PIAS: '📈',
  SIALP: '🛡',
  FUNDS: '📊',
  ANNUITY: '♾',
  LIFE_INSURANCE: '❤',
  DELAY: '⏳',
  DERISK: '⚖',
  BUFFER: '☂',
}

/** Horizontal bar: how the target retirement income splits between public pension and own savings. */
function IncomeBar({ plan }: { plan: RetirementPlan }) {
  const target = Math.max(plan.targetAnnualIncome, plan.pension.annual)
  const pensionShare = Math.min(1, plan.pension.annual / target)
  const gapShare = plan.gapAnnual / target
  return (
    <div className="income">
      <div className="income-bar" role="img" aria-label="Composición de tus ingresos en la jubilación">
        <span className="seg seg-pension" style={{ width: `${pensionShare * 100}%` }} />
        {gapShare > 0 && <span className="seg seg-savings" style={{ width: `${gapShare * 100}%` }} />}
      </div>
      <div className="income-legend small">
        <span>
          <span className="swatch seg-pension" /> Pensión pública <strong>{eur(plan.pension.annual)}/año</strong>
        </span>
        {plan.gapAnnual > 0 && (
          <span>
            <span className="swatch seg-savings" /> Tu ahorro <strong>{eur(plan.gapAnnual)}/año</strong>
          </span>
        )}
        <span className="muted">
          Objetivo {eur(plan.targetAnnualIncome)}/año ({eur(plan.targetAnnualIncome / 12)}/mes)
        </span>
      </div>
    </div>
  )
}

interface Props {
  plan: RetirementPlan
  profile: RiskProfile
  neighbourhood?: Neighbourhood | null
  neighbourhoodError?: string | null
}

export default function PlanResults({ plan, profile, neighbourhood, neighbourhoodError }: Props) {
  const ages = [...new Set(plan.scenarios.map((s) => s.retirementAge))]
  const profiles: RiskProfile[] = ['CONSERVATIVE', 'BALANCED', 'DYNAMIC']
  const projection = plan.projection.map((p) => ({ date: `${p.year}-01-01`, value: p.balance }))
  const retirementYear = plan.projection.find((p) => p.age === plan.retirementAge)?.year

  return (
    <div className="stack">
      <section className={plan.onTrack ? 'card hero-card on-track' : 'card hero-card'}>
        {plan.onTrack ? (
          <>
            <span className="hero-kicker">Con la legislación actual</span>
            <h2 className="hero-title">Tu pensión pública cubriría tu objetivo</h2>
            <p>
              Estimamos una pensión de <strong>{eur(plan.pension.monthly)}/mes</strong> (14 pagas) a los{' '}
              {plan.retirementAge} años.
              {plan.stressedMonthlySaving > 0 && (
                <>
                  {' '}
                  Aun así, si las pensiones fueran un 20 % menores necesitarías ahorrar{' '}
                  <strong>{eur(plan.stressedMonthlySaving)}/mes</strong>: es un buen colchón de prudencia.
                </>
              )}
            </p>
          </>
        ) : (
          <>
            <span className="hero-kicker">Para jubilarte a los {plan.retirementAge} con el nivel de vida que quieres</span>
            <h2 className="hero-title">
              ahorra <span className="hero-amount">{eur(plan.monthlySaving)}</span> al mes
            </h2>
            <p>
              durante {Math.round(plan.yearsToRetirement)} años, con un perfil {profileLabel[profile].toLowerCase()}.
              Si la pensión pública fuera un 20 % menor, necesitarías {eur(plan.stressedMonthlySaving)}/mes.
              {plan.productPlan?.scenario === 'BASE' && (
                <>
                  {' '}
                  Con los productos de {plan.productPlan.provider} recomendados serían{' '}
                  <strong>{eur(plan.productPlan.monthlyContribution)}/mes</strong>, porque sus comisiones restan
                  rentabilidad.
                </>
              )}
            </p>
          </>
        )}
        <IncomeBar plan={plan} />
      </section>

      {plan.productPlan && <ProductPlanSection plan={plan.productPlan} />}

      {neighbourhood && <NeighbourhoodSection data={neighbourhood} />}
      {neighbourhoodError && (
        <p className="card muted small">No hemos podido mostrar los hogares de tu barrio: {neighbourhoodError}</p>
      )}

      <section className="kpis">
        <div className="kpi">
          <span className="kpi-label">Pensión pública estimada</span>
          <span className="kpi-value">{eur(plan.pension.monthly)}</span>
          <span className="kpi-sub">al mes, 14 pagas · {pct(plan.pension.percentage, 2)} de la base</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Capital necesario a los {plan.retirementAge}</span>
          <span className="kpi-value">{eur(plan.capitalNeeded)}</span>
          <span className="kpi-sub">para {plan.horizonYears} años de jubilación</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Esperanza de vida a los {plan.retirementAge}</span>
          <span className="kpi-value">{plan.lifeExpectancy.toLocaleString('es-ES')} años</span>
          <span className="kpi-sub">Eurostat {plan.rates.lifeTableYear} · planificamos +5 años</span>
        </div>
      </section>

      {plan.recommendations.length > 0 && (
        <section className="card">
          <h3>Tu plan</h3>
          <ul className="recs">
            {plan.recommendations.map((r) => (
              <li key={r.kind}>
                <span className="rec-icon" aria-hidden="true">
                  {RECOMMENDATION_ICON[r.kind] ?? '•'}
                </span>
                <div>
                  <div className="rec-head">
                    <strong>{r.title}</strong>
                    {r.monthlyAmount != null && <span className="rec-amount">{eur(r.monthlyAmount)}/mes</span>}
                  </div>
                  <p className="muted small">{r.detail}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {plan.bankProducts && plan.bankProducts.suggestions.length > 0 && (
        <BankProductsSection products={plan.bankProducts} />
      )}

      {plan.capitalNeeded > 0 && (
        <section className="card">
          <div className="card-head">
            <h3>Evolución de tu ahorro</h3>
            <span className="muted small">
              Euros de hoy · se acumula hasta {retirementYear ?? 'la jubilación'} y luego complementa tu pensión
            </span>
          </div>
          <LineChart points={projection} unit="€" height={240} />
        </section>
      )}

      <div className="grid-2">
        <section className="card">
          <h3>Tu pensión pública</h3>
          <dl className="facts">
            <dt>Edad ordinaria de jubilación</dt>
            <dd>{plan.ordinaryAge} años</dd>
            <dt>Años cotizados al jubilarte</dt>
            <dd>{plan.pension.contributionYears.toLocaleString('es-ES')}</dd>
            <dt>Porcentaje sobre la base reguladora</dt>
            <dd>{pct(plan.pension.percentage, 2)}</dd>
            {plan.pension.delayBonusPct > 0 && (
              <>
                <dt>Complemento por demora</dt>
                <dd>+{pct(plan.pension.delayBonusPct, 0)}</dd>
              </>
            )}
            <dt>Base media de cotización (25 años)</dt>
            <dd>{eur(plan.pension.contributionBase)}/mes</dd>
            <dt>Base reguladora</dt>
            <dd>{eur(plan.pension.regulatoryBase)}/mes</dd>
            <dt>Salario final estimado</dt>
            <dd>{eur(plan.finalSalary)}/año</dd>
            <dt>Tasa de reemplazo</dt>
            <dd>{pct((plan.pension.annual / plan.finalSalary) * 100, 0)} del salario final</dd>
          </dl>
        </section>

        <section className="card">
          <h3>Escenarios: ahorro mensual necesario</h3>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Perfil</th>
                  {ages.map((a) => (
                    <th key={a} className="num">
                      A los {a}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {profiles.map((p) => (
                  <tr key={p} className={p === profile ? 'highlight' : ''}>
                    <td>{profileLabel[p]}</td>
                    {ages.map((a) => {
                      const s = plan.scenarios.find((x) => x.profile === p && x.retirementAge === a)
                      return (
                        <td key={a} className="num">
                          {s ? (s.monthlySaving > 0 ? `${eur(s.monthlySaving)}/mes` : '—') : ''}
                        </td>
                      )
                    })}
                  </tr>
                ))}
                <tr className="muted">
                  <td>Pensión estimada</td>
                  {ages.map((a) => (
                    <td key={a} className="num small">
                      {eur(plan.scenarios.find((x) => x.retirementAge === a)?.pensionMonthly ?? 0)}/mes
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
          <p className="muted small">— significa que no necesitarías ahorro adicional en ese escenario.</p>
        </section>
      </div>

      <section className="card">
        <h3>Supuestos y fuentes</h3>
        <ul className="assumptions small">
          <li>
            Rentabilidad real esperada: {pct(plan.rates.accumulationReal * 100, 2)} acumulando y{' '}
            {pct(plan.rates.retirementReal * 100, 2)} durante la jubilación. Parte del tipo a 10 años de la
            deuda AAA de la zona euro ({pct(plan.rates.safeNominal * 100, 2)} a {fmtDate(plan.rates.ratesDate)},
            BCE) menos una inflación del {pct(plan.rates.inflation * 100, 0)}, más una prima según el perfil.
          </li>
          <li>
            Esperanza de vida de Eurostat ({plan.rates.lifeTableYear}) para España por sexo y edad; el plan
            cubre {plan.horizonYears} años para protegerte si vives más de lo esperado.
          </li>
          <li>
            Crecimiento salarial real del {pct(plan.rates.realWageGrowth * 100, 1)} anual. Parámetros de la
            Seguridad Social de 2026 (base máxima 5.101,20 €/mes, pensión máxima 3.359,60 €/mes) y escala de
            porcentajes vigente desde 2027.
          </li>
          {plan.warnings.map((w) => (
            <li key={w}>{w}</li>
          ))}
        </ul>
        <p className="disclaimer small">
          Simulación orientativa en euros de hoy, no constituye asesoramiento financiero ni una estimación
          oficial de pensión. Para tu pensión pública consulta el simulador de la Seguridad Social.
        </p>
      </section>
    </div>
  )
}

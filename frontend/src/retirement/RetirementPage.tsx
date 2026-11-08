import { useRef, useState, type FormEvent } from 'react'
import PlanResults from './PlanResults'
import WorkHistoryUpload from './WorkHistoryUpload'
import {
  employmentLabel,
  profileLabel,
  requestNeighbourhood,
  requestPlan,
  type Neighbourhood,
  type Employment,
  type RetirementPlan,
  type RiskProfile,
  type Sex,
  type WorkHistory,
} from './api'

const contributes = (e: Employment) => e === 'EMPLOYEE' || e === 'SELF_EMPLOYED' || e === 'CIVIL_SERVANT'

/** Contribution years from the report, plus the time elapsed since it was issued if still registered. */
function yearsFromHistory(h: WorkHistory) {
  let years = h.contributionYears
  if (h.currentlyRegistered && h.reportDate) {
    const elapsed = (Date.now() - new Date(`${h.reportDate}T00:00:00`).getTime()) / (365.25 * 86_400_000)
    years += Math.max(0, elapsed)
  }
  return Math.round(years * 100) / 100
}

export default function RetirementPage() {
  const [birthDate, setBirthDate] = useState('')
  const [sex, setSex] = useState<Sex>('UNSPECIFIED')
  const [employment, setEmployment] = useState<Employment>('EMPLOYEE')
  const [salary, setSalary] = useState('')
  const [contributionYears, setContributionYears] = useState('')
  const [keepsContributing, setKeepsContributing] = useState(true)
  const [savings, setSavings] = useState('0')
  const [retirementAge, setRetirementAge] = useState('')
  const [target, setTarget] = useState(75)
  const [profile, setProfile] = useState<RiskProfile>('BALANCED')
  const [dependents, setDependents] = useState('0')
  const [wageGrowth, setWageGrowth] = useState('0.5')
  const [address, setAddress] = useState('')
  const [addressFromReport, setAddressFromReport] = useState(false)
  const [neighbourhood, setNeighbourhood] = useState<Neighbourhood | null>(null)
  const [neighbourhoodError, setNeighbourhoodError] = useState<string | null>(null)
  const [history, setHistory] = useState<WorkHistory | null>(null)
  const [plan, setPlan] = useState<RetirementPlan | null>(null)
  const [planProfile, setPlanProfile] = useState<RiskProfile>('BALANCED')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [triedSubmit, setTriedSubmit] = useState(false)
  const results = useRef<HTMLDivElement>(null)

  const onHistory = (h: WorkHistory) => {
    setHistory(h)
    setContributionYears(String(yearsFromHistory(h)))
    if (h.currentRegime?.startsWith('AUT')) setEmployment('SELF_EMPLOYED')
    else if (h.currentlyRegistered) setEmployment('EMPLOYEE')
    else if (h.periods[0]?.employer.includes('DESEMPLEO')) setEmployment('UNEMPLOYED')
    if (h.address && !address.trim()) {
      setAddress(h.address)
      setAddressFromReport(true)
    }
  }

  const missing = [
    !birthDate && 'fecha de nacimiento',
    salary === '' && 'salario bruto anual',
    contributionYears === '' && 'años cotizados (o sube tu vida laboral)',
  ].filter(Boolean)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (missing.length > 0) {
      setTriedSubmit(true)
      return
    }
    setError(null)
    setBusy(true)
    try {
      const where = address.trim()
      const hood = where ? requestNeighbourhood(where) : Promise.resolve(null)
      const planRequest = requestPlan({
        birthDate,
        sex,
        employment,
        grossAnnualSalary: Number(salary),
        contributionYears: Number(contributionYears),
        keepsContributing,
        retirementAge: retirementAge ? Number(retirementAge) : null,
        currentSavings: Number(savings) || 0,
        targetReplacementPct: target,
        riskProfile: profile,
        dependents: Number(dependents) || 0,
        realWageGrowthPct: Number(wageGrowth) || 0,
      })
      const [planResult, hoodResult] = await Promise.allSettled([planRequest, hood])
      if (planResult.status === 'rejected') throw planResult.reason
      const result = planResult.value
      if (hoodResult.status === 'fulfilled') {
        setNeighbourhood(hoodResult.value)
        setNeighbourhoodError(null)
      } else {
        setNeighbourhood(null)
        const reason = hoodResult.reason
        setNeighbourhoodError(reason instanceof Error ? reason.message : String(reason))
      }
      setPlan(result)
      setPlanProfile(profile)
      requestAnimationFrame(() => results.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="retirement">
      <header className="landing">
        <h1>¿Cuánto necesitas ahorrar para jubilarte bien?</h1>
        <p className="muted">
          Sube tu vida laboral, cuéntanos tu situación y calculamos tu pensión pública estimada y el plan de ahorro
          que la complementa. Usamos la esperanza de vida de Eurostat y los tipos de interés del BCE.
        </p>
      </header>

      <div className="retirement-grid">
        <form className="card plan-form" onSubmit={submit} noValidate>
          <h2>1. Tu vida laboral</h2>
          <WorkHistoryUpload history={history} onLoaded={onHistory} onClear={() => setHistory(null)} />

          <h2>2. Tu situación</h2>
          <div className="form-grid">
            <label>
              Fecha de nacimiento
              <input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} required />
            </label>
            <label>
              Sexo <span className="hint">(para la esperanza de vida)</span>
              <select value={sex} onChange={(e) => setSex(e.target.value as Sex)}>
                <option value="FEMALE">Mujer</option>
                <option value="MALE">Hombre</option>
                <option value="UNSPECIFIED">Prefiero no decirlo</option>
              </select>
            </label>
            <label>
              Situación laboral
              <select
                value={employment}
                onChange={(e) => {
                  const value = e.target.value as Employment
                  setEmployment(value)
                  setKeepsContributing(contributes(value))
                }}
              >
                {Object.entries(employmentLabel).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Salario bruto anual (€)
              <input
                type="number"
                min={0}
                step={500}
                inputMode="numeric"
                value={salary}
                onChange={(e) => setSalary(e.target.value)}
                placeholder="p. ej. 32000"
                required
              />
            </label>
            <label>
              Años cotizados hasta hoy
              <input
                type="number"
                min={0}
                max={60}
                step={0.01}
                value={contributionYears}
                onChange={(e) => setContributionYears(e.target.value)}
                placeholder={history ? '' : 'o sube tu vida laboral'}
                required
              />
              {history && <span className="hint">Calculado de tu informe; puedes corregirlo.</span>}
            </label>
            <label>
              Ahorro actual para la jubilación (€)
              <input type="number" min={0} step={1000} value={savings} onChange={(e) => setSavings(e.target.value)} />
            </label>
            <label className="check wide">
              <input
                type="checkbox"
                checked={keepsContributing}
                onChange={(e) => setKeepsContributing(e.target.checked)}
              />
              Seguiré cotizando hasta jubilarme
            </label>
            <label className="wide">
              Tu dirección <span className="hint">(opcional, para ver los hogares de tu barrio)</span>
              <input
                value={address}
                onChange={(e) => {
                  setAddress(e.target.value)
                  setAddressFromReport(false)
                }}
                placeholder="p. ej. Calle de Alcalá 100, Madrid"
                maxLength={200}
                autoComplete="street-address"
              />
              <span className="hint">
                {addressFromReport ? 'Tomada de tu vida laboral; puedes corregirla. ' : ''}Se localiza con
                CartoCiudad (IGN) y no se guarda.
              </span>
            </label>
          </div>

          <h2>3. Tus objetivos</h2>
          <div className="form-grid">
            <label className="wide">
              Ingresos que quieres en la jubilación: <strong>{target} %</strong> de tu salario final
              <input type="range" min={50} max={150} step={5} value={target} onChange={(e) => setTarget(Number(e.target.value))} />
              <span className="hint">
                {target > 100
                  ? 'Más ingresos que tu salario final: necesitarás bastante más ahorro propio, porque la pensión pública nunca supera el 100 % de tu base.'
                  : 'Lo habitual es planificar entre el 70 % y el 80 %.'}
              </span>
            </label>
            <label>
              Edad de jubilación
              <select value={retirementAge} onChange={(e) => setRetirementAge(e.target.value)}>
                <option value="">La ordinaria (65 o 67)</option>
                {[65, 66, 67, 68, 69, 70, 71, 72].map((a) => (
                  <option key={a} value={a}>
                    {a} años
                  </option>
                ))}
              </select>
            </label>
            <label>
              Personas a tu cargo
              <input type="number" min={0} max={20} value={dependents} onChange={(e) => setDependents(e.target.value)} />
            </label>
            <div className="wide">
              <span className="label">Perfil de riesgo</span>
              <div className="segmented full" role="group" aria-label="Perfil de riesgo">
                {(Object.keys(profileLabel) as RiskProfile[]).map((p) => (
                  <button type="button" key={p} className={profile === p ? 'active' : ''} onClick={() => setProfile(p)}>
                    {profileLabel[p]}
                  </button>
                ))}
              </div>
            </div>
            <details className="wide advanced">
              <summary className="small">Supuestos avanzados</summary>
              <label>
                Crecimiento salarial real anual (%)
                <input type="number" min={-3} max={5} step={0.1} value={wageGrowth} onChange={(e) => setWageGrowth(e.target.value)} />
              </label>
            </details>
          </div>

          {error && <p className="error">{error}</p>}
          <button type="submit" className="cta" disabled={busy}>
            {busy ? 'Calculando…' : 'Calcular mi plan'}
          </button>
          {missing.length > 0 && (
            <p className={triedSubmit ? 'error small' : 'muted small'}>Para calcular falta: {missing.join(', ')}.</p>
          )}
        </form>

        <div ref={results} className="plan-results">
          {plan ? (
            <PlanResults
              plan={plan}
              profile={planProfile}
              neighbourhood={neighbourhood}
              neighbourhoodError={neighbourhoodError}
            />
          ) : (
            <section className="card placeholder">
              <h2>Tu plan aparecerá aquí</h2>
              <ul className="muted">
                <li>Tu pensión pública estimada con las reglas de 2026–2027.</li>
                <li>Cuánto ahorrar al mes y en qué productos (plan de pensiones, PIAS, SIALP…).</li>
                <li>Cuánto capital necesitas según tu esperanza de vida.</li>
                <li>Escenarios por perfil de riesgo y edad de jubilación.</li>
              </ul>
            </section>
          )}
        </div>
      </div>
    </div>
  )
}

import { useMemo, useState } from 'react'
import type { Navigate } from '../App'
import LineChart from './LineChart'
import SeverityBadge from './SeverityBadge'
import {
  conditionLabel,
  fmt,
  fmtDate,
  listAlerts,
  listRules,
  listSeries,
  seriesPoints,
  type Series,
} from './api'
import { useLoad } from './useLoad'

const SOURCE_NAMES: Record<string, string> = {
  momo: 'Mortalidad (MoMo)',
  meteo: 'Temperaturas',
  ecb: 'Tipos de interés (BCE)',
  eurostat: 'Esperanza de vida',
  habits: 'Hogares (Habits)',
}

const RANGES = [
  { id: '3m', label: '3 meses', days: 92 },
  { id: '1y', label: '1 año', days: 366 },
  { id: '5y', label: '5 años', days: 1827 },
  { id: 'all', label: 'Todo', days: 0 },
] as const

type RangeId = (typeof RANGES)[number]['id']

const defaultRange = (s: Series | undefined): RangeId =>
  s?.frequency === 'anual' || s?.source === 'meteo' ? 'all' : '1y'

function fromDate(range: RangeId, last: string | null) {
  const r = RANGES.find((x) => x.id === range)!
  if (!r.days || !last) return undefined
  const d = new Date(`${last}T00:00:00`)
  d.setDate(d.getDate() - r.days)
  return d.toISOString().slice(0, 10)
}

function SeriesDetail({ series, navigate }: { series: Series; navigate: Navigate }) {
  const [range, setRange] = useState<RangeId>(defaultRange(series))
  const points = useLoad(() => seriesPoints(series.code, fromDate(range, series.lastDate)), [series.code, range])
  const rules = useLoad(listRules)
  const alerts = useLoad(() => listAlerts(), [series.code])

  const ownRules = rules.data?.filter((r) => r.seriesCode === series.code) ?? []
  const thresholds = ownRules
    .filter((r) => r.condition !== 'CHANGE_PCT' && r.enabled)
    .map((r) => ({ value: r.threshold, label: `${r.condition === 'ABOVE' ? '>' : '<'} ${fmt(r.threshold)}` }))
  const alertDates = useMemo(
    () => new Set(alerts.data?.filter((a) => a.seriesCode === series.code).map((a) => a.pointDate)),
    [alerts.data, series.code],
  )

  return (
    <section className="card">
      <div className="card-head">
        <div>
          <h2>{series.name}</h2>
          <span className="muted small">
            <code>{series.code}</code> · {series.frequency} · {series.unit}
          </span>
        </div>
        <div className="hero">
          <span className="hero-value">{fmt(series.lastValue, series.unit)}</span>
          <span className="muted small">a {fmtDate(series.lastDate)}</span>
        </div>
      </div>
      {series.description && <p className="muted">{series.description}</p>}

      <div className="segmented" role="group" aria-label="Periodo">
        {RANGES.map((r) => (
          <button key={r.id} className={range === r.id ? 'active' : ''} onClick={() => setRange(r.id)}>
            {r.label}
          </button>
        ))}
      </div>

      {points.loading && !points.data ? (
        <p className="muted">Cargando…</p>
      ) : (
        <LineChart points={points.data ?? []} unit={series.unit} thresholds={thresholds} alertDates={alertDates} />
      )}
      <p className="muted small legend-note">
        <span className="swatch line" /> valor · <span className="swatch dashed" /> umbral de regla ·{' '}
        <span className="swatch dot" /> punto con alerta
      </p>

      <div className="card-head">
        <h3>Reglas sobre esta serie</h3>
        <button onClick={() => navigate('rules', { id: series.code })}>Nueva regla</button>
      </div>
      {ownRules.length === 0 ? (
        <p className="muted">Sin reglas todavía.</p>
      ) : (
        <ul className="rule-chips">
          {ownRules.map((r) => (
            <li key={r.id} className={r.enabled ? '' : 'disabled'}>
              <SeverityBadge severity={r.severity} /> {r.name}{' '}
              <span className="muted small">
                ({conditionLabel[r.condition]} {fmt(r.threshold)})
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default function SeriesPage({ selected, navigate }: { selected?: string; navigate: Navigate }) {
  const series = useLoad(listSeries)
  const [query, setQuery] = useState('')

  const groups = useMemo(() => {
    const q = query.trim().toLowerCase()
    const map = new Map<string, Series[]>()
    for (const s of series.data ?? []) {
      if (q && !s.name.toLowerCase().includes(q) && !s.code.includes(q)) continue
      map.set(s.source, [...(map.get(s.source) ?? []), s])
    }
    return [...map.entries()]
  }, [series.data, query])

  const current = series.data?.find((s) => s.code === selected) ?? series.data?.[0]

  if (series.error) return <p className="error">{series.error}</p>
  if (series.data?.length === 0)
    return <p className="muted">Aún no hay series. Carga alguna fuente desde el Resumen.</p>

  return (
    <div className="split">
      <aside className="card series-list">
        <input
          type="search"
          placeholder="Buscar serie…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Buscar serie"
        />
        {groups.map(([source, items]) => (
          <details key={source} open={query !== '' || items.some((s) => s.code === current?.code)}>
            <summary>
              {SOURCE_NAMES[source] ?? source} <span className="muted small">{items.length}</span>
            </summary>
            <ul>
              {items.map((s) => (
                <li key={s.code}>
                  <button
                    className={s.code === current?.code ? 'series-item active' : 'series-item'}
                    onClick={() => navigate('series', { id: s.code })}
                  >
                    <span>{s.name}</span>
                    <span className="muted small">{fmt(s.lastValue, s.unit)}</span>
                  </button>
                </li>
              ))}
            </ul>
          </details>
        ))}
      </aside>
      {current && <SeriesDetail key={current.code} series={current} navigate={navigate} />}
    </div>
  )
}

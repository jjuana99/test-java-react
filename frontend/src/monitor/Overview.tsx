import { useEffect, useState } from 'react'
import type { Navigate } from '../App'
import SeverityBadge from './SeverityBadge'
import {
  fmt,
  fmtDate,
  fmtDateTime,
  ingestSource,
  listAlerts,
  listSeries,
  listSources,
  momoAgeGroups,
  warehouseTables,
  type AgeGroupExcess,
} from './api'
import { useLoad } from './useLoad'

const runLabel = { RUNNING: 'En curso', SUCCESS: 'Correcta', FAILED: 'Fallida' }

/** Diverging bars around zero: excess (red) vs deficit (blue) mortality per age group. */
function AgeGroupBars({ rows }: { rows: AgeGroupExcess[] }) {
  const max = Math.max(5, ...rows.map((r) => Math.abs(r.excessPct)))
  return (
    <div className="diverging" role="table" aria-label="Exceso de mortalidad por edad">
      {rows.map((r) => {
        const w = (Math.abs(r.excessPct) / max) * 50
        const positive = r.excessPct >= 0
        return (
          <div
            className="div-row"
            role="row"
            key={r.ageGroup}
            title={`${r.label}: ${fmt(r.observed)} observadas vs ${fmt(r.expected)} esperadas`}
          >
            <span role="cell" className="div-label">{r.label.replace('edad ', '')}</span>
            <span role="cell" className="div-track">
              <span className="div-zero" />
              <span
                className={positive ? 'div-bar pos' : 'div-bar neg'}
                style={positive ? { left: '50%', width: `${w}%` } : { right: '50%', width: `${w}%` }}
              />
            </span>
            <span role="cell" className="div-value">
              {r.excessPct > 0 ? '+' : ''}
              {fmt(r.excessPct, '%')}
            </span>
          </div>
        )
      })}
    </div>
  )
}

export default function Overview({ navigate }: { navigate: Navigate }) {
  const sources = useLoad(listSources)
  const alerts = useLoad(() => listAlerts('OPEN'))
  const series = useLoad(listSeries)
  const tables = useLoad(warehouseTables)
  const ages = useLoad(momoAgeGroups)
  const [error, setError] = useState<string | null>(null)

  const running = sources.data?.some((s) => s.lastRun?.status === 'RUNNING') ?? false

  // poll while an ingestion is running, then refresh everything once it finishes
  useEffect(() => {
    if (!running) return
    const id = window.setInterval(() => sources.reload(), 3000)
    return () => {
      window.clearInterval(id)
      alerts.reload()
      series.reload()
      tables.reload()
      ages.reload()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running])

  const ingest = async (id: string) => {
    setError(null)
    try {
      await ingestSource(id)
      await sources.reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  const rawRows = tables.data?.filter((t) => t.name !== 'points').reduce((s, t) => s + t.rows, 0) ?? 0
  const pointRows = tables.data?.find((t) => t.name === 'points')?.rows ?? 0
  const critical = alerts.data?.filter((a) => a.severity === 'CRITICAL').length ?? 0

  return (
    <div className="stack">
      <section className="kpis">
        <button className="kpi" onClick={() => navigate('alerts')}>
          <span className="kpi-label">Alertas abiertas</span>
          <span className="kpi-value">{alerts.data?.length ?? '—'}</span>
          <span className="kpi-sub">{critical} críticas</span>
        </button>
        <button className="kpi" onClick={() => navigate('series')}>
          <span className="kpi-label">Series monitorizadas</span>
          <span className="kpi-value">{series.data?.length ?? '—'}</span>
          <span className="kpi-sub">{sources.data?.length ?? 0} fuentes</span>
        </button>
        <div className="kpi">
          <span className="kpi-label">Filas en bruto (DuckDB)</span>
          <span className="kpi-value">{fmt(rawRows)}</span>
          <span className="kpi-sub">{fmt(pointRows)} puntos de series</span>
        </div>
      </section>

      {error && <p className="error">{error}</p>}

      <section className="card">
        <div className="card-head">
          <h2>Fuentes de datos</h2>
          <span className="muted">Las APIs se actualizan cada día a las 06:00</span>
        </div>
        <ul className="sources">
          {sources.data?.map((s) => (
            <li key={s.id}>
              <div className="source-main">
                <a href={s.url} target="_blank" rel="noreferrer">
                  <strong>{s.name}</strong>
                </a>
                <p className="muted">{s.description}</p>
              </div>
              <div className="source-run">
                {s.lastRun ? (
                  <>
                    <span className={`run run-${s.lastRun.status.toLowerCase()}`}>
                      {runLabel[s.lastRun.status]}
                    </span>
                    <span className="muted small">
                      {fmtDateTime(s.lastRun.finishedAt ?? s.lastRun.startedAt)}
                      {s.lastRun.status === 'SUCCESS' && s.lastRun.seriesCount > 0 && ` · ${s.lastRun.seriesCount} series`}
                    </span>
                    {s.lastRun.status === 'FAILED' && (
                      <span className="error small" title={s.lastRun.message ?? ''}>
                        {s.lastRun.message?.split('\n')[0].slice(0, 90)}
                      </span>
                    )}
                  </>
                ) : (
                  <span className="muted small">Sin cargar</span>
                )}
              </div>
              <button
                className="secondary"
                disabled={s.lastRun?.status === 'RUNNING'}
                onClick={() => ingest(s.id)}
              >
                {s.lastRun?.status === 'RUNNING' ? 'Cargando…' : s.scheduled ? 'Actualizar' : 'Importar'}
              </button>
            </li>
          ))}
        </ul>
      </section>

      <div className="grid-2">
        <section className="card">
          <div className="card-head">
            <h2>Últimas alertas abiertas</h2>
            <button className="link" onClick={() => navigate('alerts')}>Ver todas</button>
          </div>
          {alerts.data?.length === 0 && <p className="muted">No hay alertas abiertas.</p>}
          <ul className="alert-list">
            {alerts.data?.slice(0, 6).map((a) => (
              <li key={a.id}>
                <SeverityBadge severity={a.severity} />
                <div>
                  <button className="link" onClick={() => navigate('series', { id: a.seriesCode })}>
                    {a.ruleName}
                  </button>
                  <span className="muted small">
                    {fmtDate(a.pointDate)} · {a.message}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <div className="card-head">
            <h2>Exceso de mortalidad por edad</h2>
            <span className="muted small">España, últimos 28 días con datos consolidados</span>
          </div>
          {ages.data && ages.data.length > 0 ? (
            <>
              <AgeGroupBars rows={ages.data} />
              <p className="muted small legend-note">
                <span className="swatch pos" /> más defunciones de las esperadas{' '}
                <span className="swatch neg" /> menos de las esperadas · Fuente: MoMo (ISCIII)
              </p>
            </>
          ) : (
            <p className="muted">Carga la fuente MoMo para ver el desglose por edad.</p>
          )}
        </section>
      </div>
    </div>
  )
}

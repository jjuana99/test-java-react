import { useState } from 'react'
import type { Navigate } from '../App'
import SeverityBadge from './SeverityBadge'
import { acknowledgeAlert, fmt, fmtDate, fmtDateTime, listAlerts, type AlertStatus } from './api'
import { useLoad } from './useLoad'

export default function AlertsPage({ navigate, onChange }: { navigate: Navigate; onChange: () => void }) {
  const [status, setStatus] = useState<AlertStatus | undefined>('OPEN')
  const alerts = useLoad(() => listAlerts(status), [status])
  const [error, setError] = useState<string | null>(null)

  const ack = async (ids: number[]) => {
    setError(null)
    try {
      await Promise.all(ids.map(acknowledgeAlert))
      await alerts.reload()
      onChange()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  const open = alerts.data?.filter((a) => a.status === 'OPEN') ?? []

  return (
    <section className="card">
      <div className="card-head">
        <h2>Alertas</h2>
        <div className="row">
          <div className="segmented" role="group" aria-label="Estado">
            <button className={status === 'OPEN' ? 'active' : ''} onClick={() => setStatus('OPEN')}>
              Abiertas
            </button>
            <button className={status === 'ACKNOWLEDGED' ? 'active' : ''} onClick={() => setStatus('ACKNOWLEDGED')}>
              Reconocidas
            </button>
            <button className={status === undefined ? 'active' : ''} onClick={() => setStatus(undefined)}>
              Todas
            </button>
          </div>
          {open.length > 1 && (
            <button className="secondary" onClick={() => ack(open.map((a) => a.id))}>
              Reconocer todas ({open.length})
            </button>
          )}
        </div>
      </div>

      {(error || alerts.error) && <p className="error">{error ?? alerts.error}</p>}
      {alerts.data?.length === 0 && <p className="muted">No hay alertas en este estado.</p>}

      {alerts.data && alerts.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Severidad</th>
                <th>Regla</th>
                <th>Fecha dato</th>
                <th className="num">Valor</th>
                <th>Detalle</th>
                <th>Detectada</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {alerts.data.map((a) => (
                <tr key={a.id} className={a.status === 'ACKNOWLEDGED' ? 'dim' : ''}>
                  <td>
                    <SeverityBadge severity={a.severity} />
                  </td>
                  <td>
                    <button className="link" onClick={() => navigate('series', { id: a.seriesCode })}>
                      {a.ruleName}
                    </button>
                    <div className="muted small">
                      <code>{a.seriesCode}</code>
                    </div>
                  </td>
                  <td>{fmtDate(a.pointDate)}</td>
                  <td className="num">{fmt(a.value)}</td>
                  <td className="small">{a.message}</td>
                  <td className="small muted">{fmtDateTime(a.triggeredAt)}</td>
                  <td>
                    {a.status === 'OPEN' ? (
                      <button className="secondary" onClick={() => ack([a.id])}>
                        Reconocer
                      </button>
                    ) : (
                      <span className="muted small">✓ {fmtDateTime(a.acknowledgedAt)}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

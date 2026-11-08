import { useRef, useState, type DragEvent } from 'react'
import { fmtDate } from '../monitor/api'
import { uploadWorkHistory, type WorkHistory } from './api'

interface Props {
  history: WorkHistory | null
  onLoaded: (history: WorkHistory) => void
  onClear: () => void
}

/** Drop zone for the Seguridad Social "Informe de vida laboral" PDF. */
export default function WorkHistoryUpload({ history, onLoaded, onClear }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [showPeriods, setShowPeriods] = useState(false)

  const handle = async (file: File | undefined) => {
    if (!file) return
    setError(null)
    if (file.type && file.type !== 'application/pdf') {
      setError('Sube el informe en formato PDF.')
      return
    }
    setBusy(true)
    try {
      onLoaded(await uploadWorkHistory(file))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    setDragging(false)
    handle(e.dataTransfer.files[0])
  }

  if (history) {
    const worked = history.periods.filter((p) => !p.assimilated)
    return (
      <div className="wh-summary">
        <div className="wh-head">
          <div>
            <strong>
              {history.years} años, {history.months} meses y {history.days} días en alta
            </strong>
            <span className="muted small">
              {history.totalDays.toLocaleString('es-ES')} días · informe a {fmtDate(history.reportDate)}
              {history.firstRegistration && ` · primera alta ${fmtDate(history.firstRegistration)}`}
            </span>
            {history.currentRegime && (
              <span className="muted small">
                {history.currentlyRegistered ? 'De alta' : 'En situación asimilada'} en el régimen{' '}
                {history.currentRegime.toLowerCase()}
              </span>
            )}
          </div>
          <button type="button" className="secondary" onClick={onClear}>
            Quitar
          </button>
        </div>
        {history.periods.length > 0 && (
          <button type="button" className="link small" onClick={() => setShowPeriods((s) => !s)}>
            {showPeriods ? 'Ocultar' : 'Ver'} {history.periods.length} situaciones ({worked.length} empleos)
          </button>
        )}
        {showPeriods && (
          <div className="table-wrap">
            <table className="compact">
              <thead>
                <tr>
                  <th>Empresa / situación</th>
                  <th>Desde</th>
                  <th>Hasta</th>
                  <th className="num">Días</th>
                </tr>
              </thead>
              <tbody>
                {history.periods.map((p, i) => (
                  <tr key={i} className={p.assimilated ? 'dim' : ''}>
                    <td>{p.employer}</td>
                    <td>{fmtDate(p.from)}</td>
                    <td>{p.to ? fmtDate(p.to) : 'actualidad'}</td>
                    <td className="num">{p.days.toLocaleString('es-ES')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {history.warnings.map((w) => (
          <p key={w} className="muted small wh-warning">
            {w}
          </p>
        ))}
      </div>
    )
  }

  return (
    <div>
      <div
        className={dragging ? 'dropzone dragging' : 'dropzone'}
        role="button"
        tabIndex={0}
        onClick={() => input.current?.click()}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <span className="dropzone-icon" aria-hidden="true">
          ⇪
        </span>
        <strong>{busy ? 'Leyendo tu vida laboral…' : 'Sube tu informe de vida laboral (PDF)'}</strong>
        <span className="muted small">
          Arrástralo aquí o haz clic. Se descarga en la sede de la Seguridad Social (Importass).
        </span>
        <input
          ref={input}
          type="file"
          accept="application/pdf,.pdf"
          hidden
          onChange={(e) => handle(e.target.files?.[0])}
        />
      </div>
      <p className="muted small privacy">
        🔒 El PDF se procesa en memoria y no se guarda. No leemos tu nombre, DNI ni número de afiliación; tu domicilio solo se usa para mostrarte tu barrio.
      </p>
      {error && <p className="error small">{error}</p>}
    </div>
  )
}

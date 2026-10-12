import { useState, type FormEvent } from 'react'
import SeverityBadge from './SeverityBadge'
import {
  conditionLabel,
  createRule,
  deleteRule,
  fmt,
  listRules,
  listSeries,
  updateRule,
  type AlertRule,
  type AlertRuleInput,
  type Condition,
  type Severity,
} from './api'
import { useLoad } from './useLoad'

const empty = (seriesCode = ''): AlertRuleInput => ({
  name: '',
  seriesCode,
  condition: 'ABOVE',
  threshold: 0,
  severity: 'WARNING',
  lookback: 7,
  enabled: true,
})

export default function RulesPage({ presetSeries, onChange }: { presetSeries?: string; onChange: () => void }) {
  const rules = useLoad(listRules)
  const series = useLoad(listSeries)
  const [form, setForm] = useState<AlertRuleInput>(() => empty(presetSeries))
  const [editingId, setEditingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const seriesName = (code: string) => series.data?.find((s) => s.code === code)?.name ?? code
  const selectedSeries = series.data?.find((s) => s.code === form.seriesCode)

  const run = async (action: () => Promise<unknown>) => {
    setError(null)
    try {
      await action()
      await rules.reload()
      onChange()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    run(async () => {
      if (editingId != null) await updateRule(editingId, form)
      else await createRule(form)
      setEditingId(null)
      setForm(empty())
    })
  }

  const edit = (r: AlertRule) => {
    setEditingId(r.id)
    setForm({
      name: r.name,
      seriesCode: r.seriesCode,
      condition: r.condition,
      threshold: r.threshold,
      severity: r.severity,
      lookback: r.lookback,
      enabled: r.enabled,
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const set = <K extends keyof AlertRuleInput>(key: K, value: AlertRuleInput[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  return (
    <div className="stack">
      <section className="card">
        <h2>{editingId != null ? 'Editar regla' : 'Nueva regla'}</h2>
        <form className="rule-form" onSubmit={submit}>
          <label className="wide">
            Nombre
            <input value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={150} required />
          </label>
          <label className="wide">
            Serie
            <select value={form.seriesCode} onChange={(e) => set('seriesCode', e.target.value)} required>
              <option value="">Selecciona una serie…</option>
              {series.data?.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.name}
                </option>
              ))}
            </select>
            {selectedSeries && (
              <span className="muted small">
                Último valor: {fmt(selectedSeries.lastValue, selectedSeries.unit)}
              </span>
            )}
          </label>
          <label>
            Condición
            <select value={form.condition} onChange={(e) => set('condition', e.target.value as Condition)}>
              <option value="ABOVE">Por encima de</option>
              <option value="BELOW">Por debajo de</option>
              <option value="CHANGE_PCT">Variación % (±) mayor que</option>
            </select>
          </label>
          <label>
            Umbral {form.condition === 'CHANGE_PCT' ? '(%)' : selectedSeries?.unit ? `(${selectedSeries.unit})` : ''}
            <input
              type="number"
              step="any"
              value={form.threshold}
              onChange={(e) => set('threshold', Number(e.target.value))}
              required
            />
          </label>
          <label>
            Severidad
            <select value={form.severity} onChange={(e) => set('severity', e.target.value as Severity)}>
              <option value="INFO">Info</option>
              <option value="WARNING">Aviso</option>
              <option value="CRITICAL">Crítica</option>
            </select>
          </label>
          <label>
            Puntos a revisar
            <input
              type="number"
              min={1}
              max={366}
              value={form.lookback}
              onChange={(e) => set('lookback', Number(e.target.value))}
            />
          </label>
          <div className="form-actions wide">
            <button type="submit" disabled={!form.name.trim() || !form.seriesCode}>
              {editingId != null ? 'Guardar' : 'Crear regla'}
            </button>
            {editingId != null && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setEditingId(null)
                  setForm(empty())
                }}
              >
                Cancelar
              </button>
            )}
            <span className="muted small">La regla se evalúa al guardarla y tras cada ingesta de su fuente.</span>
          </div>
        </form>
        {error && <p className="error">{error}</p>}
      </section>

      <section className="card">
        <h2>Reglas ({rules.data?.length ?? 0})</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Activa</th>
                <th>Regla</th>
                <th>Condición</th>
                <th>Severidad</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rules.data?.map((r) => (
                <tr key={r.id} className={r.enabled ? '' : 'dim'}>
                  <td>
                    <input
                      type="checkbox"
                      checked={r.enabled}
                      aria-label={`Activar ${r.name}`}
                      onChange={() => run(() => updateRule(r.id, { ...r, enabled: !r.enabled }))}
                    />
                  </td>
                  <td>
                    {r.name}
                    <div className="muted small">{seriesName(r.seriesCode)}</div>
                  </td>
                  <td className="small">
                    {conditionLabel[r.condition]} {fmt(r.threshold)}
                    {r.condition === 'CHANGE_PCT' ? ' %' : ''} · últimos {r.lookback} puntos
                  </td>
                  <td>
                    <SeverityBadge severity={r.severity} />
                  </td>
                  <td>
                    <div className="actions">
                      <button className="secondary" onClick={() => edit(r)}>
                        Editar
                      </button>
                      <button className="danger" onClick={() => run(() => deleteRule(r.id))}>
                        Borrar
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

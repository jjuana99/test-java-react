import { useState } from 'react'
import { pct, type Neighbourhood, type TypologyShare } from './api'

type Reference = 'national' | 'province'

const referenceOf = (t: TypologyShare, ref: Reference) => (ref === 'national' ? t.nationalShare : t.provinceShare)

/** The typology that most exceeds the reference, among those with a meaningful weight. */
function standout(rows: TypologyShare[], ref: Reference) {
  return rows
    .filter((t) => t.share >= 5 && referenceOf(t, ref) > 0)
    .sort((a, b) => b.share / referenceOf(b, ref) - a.share / referenceOf(a, ref))[0]
}

const label = (t: TypologyShare) => (t.name ? `${t.code} · ${t.name}` : `Tipología ${t.code}`)

/** Household typologies of the census section where the person lives, against Spain or the province. */
export default function NeighbourhoodSection({ data }: { data: Neighbourhood }) {
  const [ref, setRef] = useState<Reference>('national')
  const rows = data.typologies
  const max = Math.max(...rows.map((t) => Math.max(t.share, referenceOf(t, ref))), 1)
  const top = rows[0]
  const highlight = standout(rows, ref)
  const refName = ref === 'national' ? 'España' : (data.province ?? 'tu provincia')

  return (
    <section className="card neighbourhood">
      <div className="card-head">
        <div>
          <h3>Los hogares de tu barrio</h3>
          <span className="muted small">
            {data.address}
            {data.municipality && ` · ${data.municipality}`} · sección censal {data.cusec} ·{' '}
            {data.families.toLocaleString('es-ES')} familias · Habits {data.year}
          </span>
        </div>
        <div className="segmented" role="group" aria-label="Comparar con">
          <button className={ref === 'national' ? 'active' : ''} onClick={() => setRef('national')}>
            España
          </button>
          <button className={ref === 'province' ? 'active' : ''} onClick={() => setRef('province')}>
            {data.province ?? 'Provincia'}
          </button>
        </div>
      </div>

      <p className="neighbourhood-summary">
        La tipología más común en tu sección es <strong>{label(top)}</strong> ({pct(top.share)}, frente al{' '}
        {pct(referenceOf(top, ref))} en {refName}).
        {highlight && highlight.code !== top.code && (
          <>
            {' '}
            Lo más característico frente a {refName}: <strong>{label(highlight)}</strong>, con{' '}
            {(highlight.share / referenceOf(highlight, ref)).toLocaleString('es-ES', { maximumFractionDigits: 1 })}{' '}
            veces su peso medio.
          </>
        )}
      </p>

      <ul className="typology-bars" aria-label={`Peso de cada tipología en tu sección frente a ${refName}`}>
        {rows.map((t) => {
          const reference = referenceOf(t, ref)
          return (
            <li
              key={t.code}
              className={t.share === 0 ? 'empty' : ''}
              title={`${label(t)}${t.group ? ` (${t.group})` : ''}: ${pct(t.share)} en tu sección · ${pct(
                t.provinceShare,
              )} en ${data.province ?? 'tu provincia'} · ${pct(t.nationalShare)} en España · ${t.families} familias`}
            >
              <span className="tb-label">
                <span className="code-chip">{t.code}</span> {t.name ?? ''}
              </span>
              <span className="tb-track">
                <span className="tb-bar" style={{ width: `${(t.share / max) * 100}%` }} />
                <span className="tb-ref" style={{ left: `${(reference / max) * 100}%` }} />
              </span>
              <span className="tb-value">
                {pct(t.share)}
                <span className="muted small"> / {pct(reference)}</span>
              </span>
            </li>
          )
        })}
      </ul>
      <p className="legend-note muted small">
        <span className="swatch tb-swatch" /> tu sección censal · <span className="swatch tb-ref-swatch" /> media de{' '}
        {refName}. Datos de Habits {data.year} sobre los contornos del Censo 2011 (INE). Tu dirección se ha
        localizado con CartoCiudad (IGN) y no se guarda.
      </p>
    </section>
  )
}

import { useMemo, useState } from 'react'
import type { Navigate } from '../App'
import { fmt, habitsProvinces, habitsTypologies, type Typology } from './api'
import { useLoad } from './useLoad'

const YEARS = [2007, 2008, 2009, 2010, 2011]

function TypologyCatalog({ typologies, navigate }: { typologies: Typology[]; navigate: Navigate }) {
  // rows grouped by the catalog's aggregate group, typologies without one at the end
  const rows = useMemo(
    () =>
      [...typologies].sort(
        (a, b) => (a.group ?? '~').localeCompare(b.group ?? '~', 'es') || a.code.localeCompare(b.code),
      ),
    [typologies],
  )
  const maxShare = Math.max(1, ...typologies.map((t) => t.share))

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tipología</th>
            <th>Grupo</th>
            <th>Rentas de sus subtipologías</th>
            <th className="num">Familias</th>
            <th className="bar-col">Cuota nacional</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => (
            <tr key={t.code}>
              <td>
                <button className="link" onClick={() => navigate('series', { id: `habits.es.cuota.${t.code}` })}>
                  <span className="code-chip">{t.code}</span> {t.name ?? `Tipología ${t.code}`}
                </button>
              </td>
              <td className="small">{t.group ?? <span className="muted">Sin grupo en el catálogo</span>}</td>
              <td className="small muted">{t.incomeBands.join(' · ') || '—'}</td>
              <td className="num">{fmt(t.families)}</td>
              <td className="bar-col">
                <span className="bar-cell">
                  <span className="hbar" style={{ width: `${(t.share / maxShare) * 100}%` }} />
                  <span className="small">{fmt(t.share, '%')}</span>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function MarketPage({ navigate }: { navigate: Navigate }) {
  const [year, setYear] = useState(2011)
  const provinces = useLoad(() => habitsProvinces(year), [year])
  const typologies = useLoad(() => habitsTypologies(year), [year])
  const max = Math.max(1, ...(provinces.data?.map((p) => p.families) ?? []))
  const total = provinces.data?.reduce((s, p) => s + p.families, 0) ?? 0
  const hasNames = typologies.data?.some((t) => t.name) ?? false

  const yearPicker = (
    <div className="segmented" role="group" aria-label="Año">
      {YEARS.map((y) => (
        <button key={y} className={y === year ? 'active' : ''} onClick={() => setYear(y)}>
          {y}
        </button>
      ))}
    </div>
  )

  if (provinces.data?.length === 0) {
    return (
      <section className="card">
        <p className="muted">Importa la fuente Habits desde el Resumen para ver este análisis.</p>
      </section>
    )
  }

  return (
    <div className="stack">
      <section className="card">
        <div className="card-head">
          <div>
            <h2>Tipologías de hogar</h2>
            <span className="muted small">Catálogo oficial de Habits y peso de cada tipología en España · {year}</span>
          </div>
          {yearPicker}
        </div>
        {typologies.error && <p className="error">{typologies.error}</p>}
        {typologies.data && <TypologyCatalog typologies={typologies.data} navigate={navigate} />}
        {hasNames ? (
          <p className="muted small">
            Nombres, grupos y tramos de renta según los catálogos del volcado (SIEL_T_A_HCTI_CATTIPOL,
            SIEL_T_A_HCST_CATSUBTIPOL y SIEL_T_M_HCAT_AGGTIPOL). <strong>DINK</strong>: “double income, no kids”,
            pareja con dos ingresos y sin hijos. “Alta” y “baja” son la denominación original del catálogo. La
            tipología Z es el total de familias de la sección.
          </p>
        ) : (
          <p className="muted small">
            No se ha encontrado el catálogo de tipologías (carpeta <code>app.habits.catalog-dir</code>); se
            muestran solo los códigos.
          </p>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <div>
            <h2>Mercado potencial por provincia</h2>
            <span className="muted small">
              Familias por provincia y tipología de hogar dominante · Habits {year} · consulta directa sobre las{' '}
              3,4 M filas en bruto
            </span>
          </div>
        </div>

        {provinces.error && <p className="error">{provinces.error}</p>}

        {provinces.data && provinces.data.length > 0 && (
          <>
            <p>
              <strong>{fmt(total)}</strong> familias en {provinces.data.length} provincias.
            </p>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Provincia</th>
                    <th className="num">Familias</th>
                    <th className="bar-col" aria-hidden="true" />
                    <th>Tipología dominante</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {provinces.data.map((p) => (
                    <tr key={p.code}>
                      <td>
                        <button
                          className="link"
                          onClick={() =>
                            navigate('series', { id: `habits.prov.${String(p.code).padStart(2, '0')}.total` })
                          }
                        >
                          {p.name}
                        </button>
                      </td>
                      <td className="num">{fmt(p.families)}</td>
                      <td className="bar-col" aria-hidden="true">
                        <span className="hbar" style={{ width: `${(p.families / max) * 100}%` }} />
                      </td>
                      <td>
                        <span className="code-chip">{p.topTypology}</span> {p.topTypologyName ?? ''}{' '}
                        <span className="muted small">({fmt(p.topShare, '%')})</span>
                      </td>
                      <td>
                        <button className="link small" onClick={() => navigate('map', { id: String(p.code) })}>
                          Ver mapa
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </div>
  )
}

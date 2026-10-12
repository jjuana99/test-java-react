import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  fmt,
  geoProvinces,
  geoSections,
  habitsTypologies,
  typologyLabel,
  type MapMetric,
  type SectionProps,
} from './api'
import { useLoad } from './useLoad'

const YEARS = [2007, 2008, 2009, 2010, 2011]
const TYPOLOGIES = 'ABCDEFGHIJKLMNOPQR'.split('')

const METRICS: { id: MapMetric; label: string; unit: string }[] = [
  { id: 'FAMILIES', label: 'Familias', unit: 'familias' },
  { id: 'DENSITY', label: 'Densidad', unit: 'fam./km²' },
  { id: 'SHARE', label: 'Cuota de tipología', unit: '%' },
]

// sequential blue, low -> high; in dark mode the high end is the one furthest from the surface
const RAMP_LIGHT = ['#cde2fb', '#86b6ef', '#3987e5', '#1c5cab', '#0d366b']
const RAMP_DARK = ['#184f95', '#256abf', '#3987e5', '#6da7ec', '#b7d3f6']
const NO_DATA = { light: '#bdbcb7', dark: '#4a4a46' }

// IGN gray basemap: public, no API key. Dark mode inverts it with a CSS filter.
const TILES =
  'https://www.ign.es/wmts/ign-base?layer=IGNBase-gris&style=default&tilematrixset=GoogleMapsCompatible&Service=WMTS&Request=GetTile&Version=1.0.0&Format=image/jpeg&TileMatrix={z}&TileCol={x}&TileRow={y}'
const ATTRIBUTION = 'Mapa base &copy; <a href="https://www.ign.es">IGN España</a> · Secciones: INE'

function usePrefersDark() {
  const query = '(prefers-color-scheme: dark)'
  const [dark, setDark] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const mq = window.matchMedia(query)
    const onChange = (e: MediaQueryListEvent) => setDark(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])
  return dark
}

/** Quintile upper bounds of the non-null values (deduplicated). */
function quantileBreaks(values: number[], classes: number) {
  const sorted = [...values].sort((a, b) => a - b)
  const breaks: number[] = []
  for (let i = 1; i < classes; i++) {
    const b = sorted[Math.min(sorted.length - 1, Math.floor((i / classes) * sorted.length))]
    if (breaks.length === 0 || b > breaks[breaks.length - 1]) breaks.push(b)
  }
  return breaks
}

const classOf = (v: number, breaks: number[]) => {
  const i = breaks.findIndex((b) => v < b)
  return i === -1 ? breaks.length : i
}

export default function MapPage({ initialProvince }: { initialProvince?: number }) {
  const [province, setProvince] = useState(initialProvince ?? 28)
  const [year, setYear] = useState(2011)
  const [metric, setMetric] = useState<MapMetric>('SHARE')
  const [typology, setTypology] = useState('R')
  const [hovered, setHovered] = useState<SectionProps | null>(null)
  const dark = usePrefersDark()

  const provinces = useLoad(geoProvinces)
  const typologies = useLoad(() => habitsTypologies(year), [year])
  const selectedTypology = typologies.data?.find((t) => t.code === typology)
  const sections = useLoad(() => geoSections(province, year, metric, typology), [province, year, metric, typology])

  const mapEl = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.GeoJSON | null>(null)
  const fittedProvince = useRef<number | null>(null)

  const unit = METRICS.find((m) => m.id === metric)!.unit
  const ramp = dark ? RAMP_DARK : RAMP_LIGHT

  const stats = useMemo(() => {
    const features = sections.data?.features ?? []
    const values = features.map((f) => f.properties.value).filter((v): v is number => v != null)
    const breaks = values.length ? quantileBreaks(values, ramp.length) : []
    const top = features
      .filter((f) => f.properties.value != null)
      .sort((a, b) => b.properties.value! - a.properties.value!)
      .slice(0, 10)
      .map((f) => f.properties)
    const families = features.reduce((s, f) => s + (f.properties.families ?? 0), 0)
    return { breaks, top, families, total: features.length, withData: values.length,
      min: values.length ? Math.min(...values) : 0, max: values.length ? Math.max(...values) : 0 }
  }, [sections.data, ramp.length])

  // create the map and its basemap once
  useEffect(() => {
    if (!mapEl.current) return
    const m = L.map(mapEl.current, { preferCanvas: true, zoomSnap: 0.25 }).setView([40.2, -3.7], 6)
    L.tileLayer(TILES, { attribution: ATTRIBUTION, maxZoom: 19 }).addTo(m)
    map.current = m
    return () => {
      m.remove()
      map.current = null
    }
  }, [])

  // choropleth layer
  useEffect(() => {
    const m = map.current
    if (!m || !sections.data) return
    layer.current?.remove()
    const outline = dark ? '#1a1a19' : '#ffffff'
    const style = (p: SectionProps): L.PathOptions => ({
      color: outline,
      weight: 0.5,
      fillOpacity: p.value == null ? 0.35 : 0.8,
      fillColor: p.value == null ? (dark ? NO_DATA.dark : NO_DATA.light) : ramp[classOf(p.value, stats.breaks)],
    })
    const geo = L.geoJSON(sections.data, {
      style: (f) => style(f!.properties as SectionProps),
      onEachFeature: (f, l) => {
        const props = f.properties as SectionProps
        l.on({
          mouseover: () => {
            ;(l as L.Path).setStyle({ weight: 2, color: dark ? '#f0efec' : '#0b0b0b' })
            ;(l as L.Path).bringToFront()
            setHovered(props)
          },
          mouseout: () => {
            ;(l as L.Path).setStyle(style(props))
            setHovered(null)
          },
        })
      },
    }).addTo(m)
    layer.current = geo
    if (fittedProvince.current !== province && sections.data.features.length > 0) {
      m.fitBounds(geo.getBounds(), { padding: [12, 12] })
      fittedProvince.current = province
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sections.data, stats.breaks, dark])

  const legend = [stats.min, ...stats.breaks]
    .map((from, i, arr) => ({ from, to: i + 1 < arr.length ? arr[i + 1] : stats.max, color: ramp[i] }))
    .filter((c) => c.color)

  const provinceName = provinces.data?.find((p) => p.code === province)?.name ?? ''

  return (
    <div className="stack">
      <section className="card">
        <div className="card-head">
          <div>
            <h2>Mapa por sección censal</h2>
            <span className="muted small">
              Habits {year} sobre los contornos del Censo 2011 (INE)
              {year < 2011 && ' · en años anteriores a 2011 algunas secciones han cambiado y aparecen sin datos'}
            </span>
          </div>
        </div>
        <div className="map-controls">
          <label>
            Provincia
            <select value={province} onChange={(e) => setProvince(Number(e.target.value))}>
              {provinces.data?.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Indicador
            <select value={metric} onChange={(e) => setMetric(e.target.value as MapMetric)}>
              {METRICS.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.label}
                </option>
              ))}
            </select>
          </label>
          {metric === 'SHARE' && (
            <label>
              Tipología
              <select value={typology} onChange={(e) => setTypology(e.target.value)}>
                {TYPOLOGIES.map((t) => (
                  <option key={t} value={t}>
                    {typologyLabel(t, typologies.data)}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="segmented" role="group" aria-label="Año">
            {YEARS.map((y) => (
              <button key={y} className={y === year ? 'active' : ''} onClick={() => setYear(y)}>
                {y}
              </button>
            ))}
          </div>
        </div>
        {metric === 'SHARE' && selectedTypology?.name && (
          <p className="small typology-note">
            <span className="code-chip">{selectedTypology.code}</span> <strong>{selectedTypology.name}</strong>
            {selectedTypology.group && <span className="muted"> · grupo {selectedTypology.group}</span>}
            {selectedTypology.incomeBands.length > 0 && (
              <span className="muted"> · rentas: {selectedTypology.incomeBands.join(', ')}</span>
            )}
            <span className="muted"> · {fmt(selectedTypology.share, '%')} de las familias de España en {year}</span>
          </p>
        )}

        {sections.error && <p className="error">{sections.error}</p>}

        <div className="map-wrap">
          <div ref={mapEl} className={dark ? 'map dark' : 'map'} aria-label={`Mapa de secciones censales de ${provinceName}`} />
          {sections.loading && <div className="map-loading">Cargando secciones…</div>}
          <div className="map-panel map-info" aria-live="polite">
            {hovered ? (
              <>
                <strong>{hovered.municipality}</strong>
                <span className="muted small">Sección {hovered.cusec}</span>
                <span className="map-value">{fmt(hovered.value, unit)}</span>
                <span className="muted small">
                  {fmt(hovered.families)} familias · {fmt(hovered.km2)} km²
                </span>
              </>
            ) : (
              <span className="muted small">Pasa el ratón por una sección</span>
            )}
          </div>
          {legend.length > 0 && (
            <div className="map-panel map-legend">
              <strong className="small">
                {metric === 'SHARE' ? `Cuota ${typologyLabel(typology, typologies.data)}` : METRICS.find((m) => m.id === metric)!.label} ({unit})
              </strong>
              {legend.map((c) => (
                <span key={c.color} className="legend-row small">
                  <span className="swatch" style={{ background: c.color }} />
                  {fmt(c.from)} – {fmt(c.to)}
                </span>
              ))}
              <span className="legend-row small">
                <span className="swatch" style={{ background: dark ? NO_DATA.dark : NO_DATA.light, opacity: 0.6 }} />
                sin datos
              </span>
            </div>
          )}
        </div>

        {sections.data && (
          <p className="small muted">
            {fmt(stats.total)} secciones en {provinceName} · {fmt(stats.withData)} con datos de Habits ·{' '}
            {fmt(stats.families)} familias. Clases por quintiles.
          </p>
        )}
      </section>

      {stats.top.length > 0 && (
        <section className="card">
          <h3>Secciones con mayor valor</h3>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Sección</th>
                  <th>Municipio</th>
                  <th className="num">Familias</th>
                  <th className="num">{METRICS.find((m) => m.id === metric)!.label}</th>
                </tr>
              </thead>
              <tbody>
                {stats.top.map((p) => (
                  <tr key={p.cusec}>
                    <td>
                      <code>{p.cusec}</code>
                    </td>
                    <td>{p.municipality}</td>
                    <td className="num">{fmt(p.families)}</td>
                    <td className="num">{fmt(p.value, unit)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}

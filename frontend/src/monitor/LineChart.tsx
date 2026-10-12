import { useLayoutEffect, useMemo, useRef, useState, type PointerEvent } from 'react'
import { fmt, fmtDate, type Point } from './api'

interface Threshold {
  value: number
  label: string
}

interface Props {
  points: Point[]
  unit: string | null
  thresholds?: Threshold[]
  alertDates?: Set<string>
  height?: number
}

const PAD = { top: 16, right: 16, bottom: 28, left: 56 }

function niceTicks(min: number, max: number, count = 5) {
  if (min === max) {
    min -= 1
    max += 1
  }
  const raw = (max - min) / count
  const mag = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) ?? raw
  // first tick at or below min, last at or above max, so the line always stays inside the axis
  const first = Math.floor(min / step)
  const last = Math.ceil(max / step)
  const ticks: number[] = []
  for (let i = first; i <= last; i++) ticks.push(Math.abs(i * step) < step / 1e6 ? 0 : i * step)
  return ticks
}

/** Single-series line chart with crosshair tooltip, threshold rules and alert markers. */
export default function LineChart({ points, unit, thresholds = [], alertDates, height = 280 }: Props) {
  const wrap = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(600)
  const [hover, setHover] = useState<number | null>(null)

  const hasData = points.length > 0

  // the wrapper only exists once there is data; measure it right away and on every resize
  useLayoutEffect(() => {
    const el = wrap.current
    if (!el) return
    setWidth(el.getBoundingClientRect().width)
    const ro = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    ro.observe(el)
    return () => ro.disconnect()
  }, [hasData])

  const geom = useMemo(() => {
    if (!hasData) return null
    const xs = points.map((p) => new Date(`${p.date}T00:00:00`).getTime())
    const values = points.map((p) => p.value).concat(thresholds.map((t) => t.value))
    const yTicks = niceTicks(Math.min(...values), Math.max(...values))
    const y0 = yTicks[0]
    const y1 = yTicks[yTicks.length - 1]
    const x0 = xs[0]
    const x1 = xs[xs.length - 1] === x0 ? x0 + 1 : xs[xs.length - 1]
    const iw = Math.max(10, width - PAD.left - PAD.right)
    const ih = height - PAD.top - PAD.bottom
    const sx = (t: number) => PAD.left + ((t - x0) / (x1 - x0)) * iw
    const sy = (v: number) => PAD.top + ih - ((v - y0) / (y1 - y0 || 1)) * ih
    const path = points.map((p, i) => `${i ? 'L' : 'M'}${sx(xs[i]).toFixed(1)},${sy(p.value).toFixed(1)}`).join('')
    const spanDays = (x1 - x0) / 86_400_000
    const xTickCount = Math.max(2, Math.floor(iw / 90))
    const xTicks = Array.from({ length: xTickCount }, (_, i) => x0 + ((x1 - x0) * i) / (xTickCount - 1))
    const xLabel = (t: number) =>
      new Date(t).toLocaleDateString('es-ES', spanDays > 730 ? { year: 'numeric' } : spanDays > 60 ? { month: 'short', year: '2-digit' } : { day: 'numeric', month: 'short' })
    return { xs, yTicks, sx, sy, path, xTicks, xLabel, iw }
  }, [hasData, points, thresholds, width, height])

  if (!geom) return <p className="muted">Sin datos para este periodo.</p>

  const onMove = (e: PointerEvent<SVGRectElement>) => {
    const box = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - box.left + PAD.left
    let best = 0
    let bestDist = Infinity
    geom.xs.forEach((t, i) => {
      const d = Math.abs(geom.sx(t) - x)
      if (d < bestDist) {
        bestDist = d
        best = i
      }
    })
    setHover(best)
  }

  const hp = hover != null ? points[hover] : null
  const hx = hover != null ? geom.sx(geom.xs[hover]) : 0

  return (
    <div className="chart" ref={wrap}>
      <svg width={width} height={height} role="img" aria-label="Evolución de la serie">
        {geom.yTicks.map((v) => (
          <g key={v}>
            <line className="grid" x1={PAD.left} x2={PAD.left + geom.iw} y1={geom.sy(v)} y2={geom.sy(v)} />
            <text className="axis" x={PAD.left - 8} y={geom.sy(v)} textAnchor="end" dominantBaseline="middle">
              {fmt(v)}
            </text>
          </g>
        ))}
        {geom.xTicks.map((t, i) => (
          <text key={i} className="axis" x={geom.sx(t)} y={height - 8} textAnchor={i === 0 ? 'start' : i === geom.xTicks.length - 1 ? 'end' : 'middle'}>
            {geom.xLabel(t)}
          </text>
        ))}
        {thresholds.map((t) => (
          <g key={`${t.label}-${t.value}`}>
            <line className="threshold" x1={PAD.left} x2={PAD.left + geom.iw} y1={geom.sy(t.value)} y2={geom.sy(t.value)} />
            <text className="threshold-label" x={PAD.left + geom.iw} y={geom.sy(t.value) - 5} textAnchor="end">
              {t.label}
            </text>
          </g>
        ))}
        <path className="line" d={geom.path} />
        {alertDates &&
          points.map((p, i) =>
            alertDates.has(p.date) ? (
              <circle key={p.date} className="alert-dot" cx={geom.sx(geom.xs[i])} cy={geom.sy(p.value)} r={4.5} />
            ) : null,
          )}
        {hp && (
          <g>
            <line className="crosshair" x1={hx} x2={hx} y1={PAD.top} y2={height - PAD.bottom} />
            <circle className="hover-dot" cx={hx} cy={geom.sy(hp.value)} r={4.5} />
          </g>
        )}
        <rect
          x={PAD.left}
          y={PAD.top}
          width={geom.iw}
          height={height - PAD.top - PAD.bottom}
          fill="transparent"
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        />
      </svg>
      {hp && (
        <div className="tooltip" style={{ left: Math.min(Math.max(hx, 70), width - 70), top: 0 }}>
          <strong>{fmt(hp.value, unit)}</strong>
          <span>{fmtDate(hp.date)}</span>
          {alertDates?.has(hp.date) && <span className="tooltip-alert">⚠ alerta</span>}
        </div>
      )}
    </div>
  )
}

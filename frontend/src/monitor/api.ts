import { authFetch } from '../auth/firebase'
import type { FeatureCollection, Geometry } from 'geojson'

export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'
export type Condition = 'ABOVE' | 'BELOW' | 'CHANGE_PCT'
export type Severity = 'INFO' | 'WARNING' | 'CRITICAL'
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED'

export interface IngestionRun {
  id: number
  source: string
  status: RunStatus
  startedAt: string
  finishedAt: string | null
  seriesCount: number
  alertsRaised: number
  message: string | null
}

export interface Source {
  id: string
  name: string
  description: string
  url: string
  scheduled: boolean
  lastRun: IngestionRun | null
}

export interface Series {
  id: number
  code: string
  source: string
  name: string
  unit: string | null
  frequency: string | null
  description: string | null
  lastDate: string | null
  lastValue: number | null
  updatedAt: string | null
}

export interface Point {
  date: string
  value: number
}

export interface AlertRule {
  id: number
  name: string
  seriesCode: string
  condition: Condition
  threshold: number
  severity: Severity
  lookback: number
  enabled: boolean
  createdAt: string
}

export type AlertRuleInput = Omit<AlertRule, 'id' | 'createdAt'>

export interface Alert {
  id: number
  ruleId: number
  ruleName: string
  seriesCode: string
  severity: Severity
  pointDate: string
  value: number
  message: string
  status: AlertStatus
  triggeredAt: string
  acknowledgedAt: string | null
}

export interface TableStats {
  name: string
  rows: number
  columns: number
}

export interface AgeGroupExcess {
  ageGroup: string
  label: string
  observed: number
  expected: number
  excessPct: number
}

export interface ProvinceMarket {
  code: number
  name: string
  families: number
  topTypology: string
  topTypologyName: string | null
  topShare: number
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await authFetch(`/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) {
    let detail = ''
    try {
      const body = await res.json()
      detail = body.detail ?? body.message ?? ''
    } catch {
      // body without JSON
    }
    throw new Error(`Error ${res.status}${detail ? `: ${detail}` : ''}`)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

export const listSources = () => request<Source[]>('/sources')
export const ingestSource = (id: string) =>
  request<IngestionRun>(`/sources/${id}/ingest`, { method: 'POST' })

export const listSeries = () => request<Series[]>('/series')
export const seriesPoints = (code: string, from?: string) =>
  request<Point[]>(`/series/${encodeURIComponent(code)}/points${from ? `?from=${from}` : ''}`)

export const listRules = () => request<AlertRule[]>('/alert-rules')
export const createRule = (rule: AlertRuleInput) =>
  request<AlertRule>('/alert-rules', { method: 'POST', body: JSON.stringify(rule) })
export const updateRule = (id: number, rule: AlertRuleInput) =>
  request<AlertRule>(`/alert-rules/${id}`, { method: 'PUT', body: JSON.stringify(rule) })
export const deleteRule = (id: number) =>
  request<void>(`/alert-rules/${id}`, { method: 'DELETE' })

export const listAlerts = (status?: AlertStatus) =>
  request<Alert[]>(`/alerts${status ? `?status=${status}` : ''}`)
export const acknowledgeAlert = (id: number) =>
  request<Alert>(`/alerts/${id}/acknowledge`, { method: 'POST' })

export const warehouseTables = () => request<TableStats[]>('/analytics/tables')
export const momoAgeGroups = () => request<AgeGroupExcess[]>('/analytics/momo/age-groups')
/** Habits household typology, named from the official catalog when available. */
export interface Typology {
  code: string
  name: string | null
  group: string | null
  incomeBands: string[]
  families: number
  share: number
}

export const habitsTypologies = (year: number) =>
  request<Typology[]>(`/analytics/habits/typologies?year=${year}`)

export const typologyLabel = (code: string, typologies?: Typology[] | null) => {
  const name = typologies?.find((t) => t.code === code)?.name
  return name ? `${code} · ${name}` : `Tipología ${code}`
}

export const habitsProvinces = (year: number) =>
  request<ProvinceMarket[]>(`/analytics/habits/provinces?year=${year}`)

const num = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const int = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 })

export const fmt = (v: number | null | undefined, unit?: string | null) => {
  if (v == null) return '—'
  const n = Math.abs(v) >= 1000 ? int.format(v) : num.format(v)
  return unit ? `${n} ${unit}` : n
}

export const fmtDate = (iso: string | null | undefined) =>
  iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString('es-ES') : '—'

export const fmtDateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' }) : '—'

export const conditionLabel: Record<Condition, string> = {
  ABOVE: 'por encima de',
  BELOW: 'por debajo de',
  CHANGE_PCT: 'variación % mayor que ±',
}

export const severityLabel: Record<Severity, string> = {
  INFO: 'Info',
  WARNING: 'Aviso',
  CRITICAL: 'Crítica',
}

export const sourceOfCode = (code: string) => code.split('.')[0]

export type MapMetric = 'FAMILIES' | 'DENSITY' | 'SHARE'

export interface SectionProps {
  cusec: string
  municipality: string
  km2: number
  families: number | null
  value: number | null
}

export type SectionCollection = FeatureCollection<Geometry, SectionProps>

export interface Province {
  code: number
  name: string
}

export const geoProvinces = () => request<Province[]>('/geo/provinces')
export const geoSections = (province: number, year: number, metric: MapMetric, typology: string) =>
  request<SectionCollection>(
    `/geo/sections?province=${province}&year=${year}&metric=${metric}&typology=${typology}`,
  )

import { useEffect, useState } from 'react'
import Tasks from './Tasks'
import AlertsPage from './monitor/AlertsPage'
import MapPage from './monitor/MapPage'
import MarketPage from './monitor/MarketPage'
import Overview from './monitor/Overview'
import RulesPage from './monitor/RulesPage'
import SeriesPage from './monitor/SeriesPage'
import { listAlerts } from './monitor/api'
import './App.css'

const TABS = [
  { id: 'overview', label: 'Resumen' },
  { id: 'series', label: 'Series' },
  { id: 'alerts', label: 'Alertas' },
  { id: 'rules', label: 'Reglas' },
  { id: 'market', label: 'Mercado' },
  { id: 'map', label: 'Mapa' },
  { id: 'tasks', label: 'Tareas' },
] as const

export type TabId = (typeof TABS)[number]['id']

export interface Navigate {
  /** `id` is the tab's subject: a series code, or a province code for the map. */
  (tab: TabId, params?: { id?: string }): void
}

const readHash = (): { tab: TabId; id?: string } => {
  const [tab, id] = window.location.hash.slice(1).split('/')
  const known = TABS.find((t) => t.id === tab)
  return { tab: known ? known.id : 'overview', id: id ? decodeURIComponent(id) : undefined }
}

function App() {
  const [route, setRoute] = useState(readHash)
  const [openAlerts, setOpenAlerts] = useState<number | null>(null)

  useEffect(() => {
    const onHash = () => setRoute(readHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  const refreshAlertCount = () =>
    listAlerts('OPEN')
      .then((a) => setOpenAlerts(a.length))
      .catch(() => setOpenAlerts(null))

  useEffect(() => {
    refreshAlertCount()
  }, [route.tab])

  const navigate: Navigate = (tab, params) => {
    window.location.assign(`#${params?.id ? `${tab}/${encodeURIComponent(params.id)}` : tab}`)
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="logo" aria-hidden="true">◆</span>
          <div>
            <strong>Vida Monitor</strong>
            <span className="muted">Datos públicos y alertas para seguros de vida</span>
          </div>
        </div>
        <nav className="tabs" aria-label="Secciones">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={route.tab === t.id ? 'tab active' : 'tab'}
              aria-current={route.tab === t.id ? 'page' : undefined}
              onClick={() => navigate(t.id)}
            >
              {t.label}
              {t.id === 'alerts' && openAlerts ? <span className="count">{openAlerts}</span> : null}
            </button>
          ))}
        </nav>
      </header>

      <main className="content">
        {route.tab === 'overview' && <Overview navigate={navigate} />}
        {route.tab === 'series' && <SeriesPage selected={route.id} navigate={navigate} />}
        {route.tab === 'alerts' && <AlertsPage navigate={navigate} onChange={refreshAlertCount} />}
        {route.tab === 'rules' && <RulesPage presetSeries={route.id} onChange={refreshAlertCount} />}
        {route.tab === 'market' && <MarketPage navigate={navigate} />}
        {route.tab === 'map' && (
          <MapPage key={route.id} initialProvince={route.id ? Number(route.id) : undefined} />
        )}
        {route.tab === 'tasks' && <Tasks />}
      </main>
    </div>
  )
}

export default App

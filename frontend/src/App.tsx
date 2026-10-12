import { useEffect, useState, type FormEvent } from 'react'
import { createTask, deleteTask, listTasks, updateTask, type Task } from './api'
import './App.css'

function App() {
  const [tasks, setTasks] = useState<Task[]>([])
  const [title, setTitle] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingTitle, setEditingTitle] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const run = async (action: () => Promise<void>) => {
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  useEffect(() => {
    run(async () => setTasks(await listTasks())).finally(() => setLoading(false))
  }, [])

  const handleCreate = (e: FormEvent) => {
    e.preventDefault()
    if (!title.trim()) return
    run(async () => {
      const task = await createTask(title)
      setTasks((prev) => [task, ...prev])
      setTitle('')
    })
  }

  const replace = (task: Task) =>
    setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))

  const handleToggle = (task: Task) =>
    run(async () => replace(await updateTask({ ...task, done: !task.done })))

  const handleSaveEdit = (task: Task) => {
    if (!editingTitle.trim()) return
    run(async () => {
      replace(await updateTask({ ...task, title: editingTitle }))
      setEditingId(null)
    })
  }

  const handleDelete = (id: number) =>
    run(async () => {
      await deleteTask(id)
      setTasks((prev) => prev.filter((t) => t.id !== id))
    })

  const pending = tasks.filter((t) => !t.done).length

  return (
    <main className="app">
      <h1>Tareas</h1>
      <p className="subtitle">
        {loading ? 'Cargando…' : `${pending} pendiente${pending === 1 ? '' : 's'} de ${tasks.length}`}
      </p>

      <form className="new-task" onSubmit={handleCreate}>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="¿Qué hay que hacer?"
          maxLength={200}
          aria-label="Nueva tarea"
        />
        <button type="submit" disabled={!title.trim()}>
          Añadir
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      <ul className="tasks">
        {tasks.map((task) => (
          <li key={task.id} className={task.done ? 'done' : ''}>
            <input
              type="checkbox"
              checked={task.done}
              onChange={() => handleToggle(task)}
              aria-label={`Completar ${task.title}`}
            />
            {editingId === task.id ? (
              <form
                className="edit"
                onSubmit={(e) => {
                  e.preventDefault()
                  handleSaveEdit(task)
                }}
              >
                <input
                  value={editingTitle}
                  onChange={(e) => setEditingTitle(e.target.value)}
                  maxLength={200}
                  autoFocus
                  aria-label="Editar tarea"
                />
                <button type="submit">Guardar</button>
                <button type="button" className="secondary" onClick={() => setEditingId(null)}>
                  Cancelar
                </button>
              </form>
            ) : (
              <>
                <span className="title">{task.title}</span>
                <button
                  className="secondary"
                  onClick={() => {
                    setEditingId(task.id)
                    setEditingTitle(task.title)
                  }}
                >
                  Editar
                </button>
                <button className="danger" onClick={() => handleDelete(task.id)}>
                  Borrar
                </button>
              </>
            )}
          </li>
        ))}
      </ul>

      {!loading && tasks.length === 0 && <p className="empty">No hay tareas todavía.</p>}
    </main>
  )
}

export default App

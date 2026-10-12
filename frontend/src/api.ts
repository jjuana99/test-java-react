export interface Task {
  id: number
  title: string
  done: boolean
  createdAt: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/tasks${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    throw new Error(`Error ${res.status}`)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

export const listTasks = () => request<Task[]>('')

export const createTask = (title: string) =>
  request<Task>('', { method: 'POST', body: JSON.stringify({ title }) })

export const updateTask = (task: Task) =>
  request<Task>(`/${task.id}`, {
    method: 'PUT',
    body: JSON.stringify({ title: task.title, done: task.done }),
  })

export const deleteTask = (id: number) =>
  request<void>(`/${id}`, { method: 'DELETE' })

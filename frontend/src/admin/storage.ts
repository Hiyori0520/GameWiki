import { ref } from 'vue'
import type { Ref } from 'vue'
import { isAdminDemo } from '../api/admin/client'

export interface DemoActivity {
  collection: string
  label: string
  action: '新增' | '更新' | '删除'
  time: string
}

const activityKey = 'gamewiki:admin-demo:v1:activity'
export const demoActivity = ref<DemoActivity[]>([])
try {
  const saved: unknown = isAdminDemo ? JSON.parse(localStorage.getItem(activityKey) ?? '[]') : []
  if (Array.isArray(saved)) demoActivity.value = saved
} catch {
  // The activity list starts empty when browser storage is unavailable.
}

function recordLabel(record: { id: number }) {
  const fields = record as { id: number; name?: string; title?: string }
  return fields.title ?? fields.name ?? `#${fields.id}`
}

export function createDemoCollection<T extends { id: number }>(key: string, seed: T[]) {
  const storageKey = `gamewiki:admin-demo:v1:${key}`
  const records = ref<T[]>(isAdminDemo ? seed : []) as Ref<T[]>
  let lastSaved: string | null = null

  try {
    lastSaved = isAdminDemo ? localStorage.getItem(storageKey) : null
    if (lastSaved) {
      const parsed: unknown = JSON.parse(lastSaved)
      if (Array.isArray(parsed) && parsed.every((item) => item && Number.isSafeInteger(item.id))) {
        records.value = parsed
      }
    }
  } catch {
    // The sample collection remains available when browser storage cannot be read.
  }

  function replace(next: T[]) {
    if (!isAdminDemo) throw new Error('当前环境已关闭开发演示数据。')
    if (localStorage.getItem(storageKey) !== lastSaved) {
      throw new Error('演示数据已在其他页面更新，请刷新后重试。')
    }
    const previous = new Map(records.value.map((record) => [record.id, record]))
    const serialized = JSON.stringify(next)
    localStorage.setItem(storageKey, serialized)
    lastSaved = serialized
    records.value = next
    const time = new Date().toISOString()
    const changes: DemoActivity[] = []
    for (const record of next) {
      const before = previous.get(record.id)
      previous.delete(record.id)
      if (before !== record && (!before || JSON.stringify(before) !== JSON.stringify(record))) {
        changes.push({ collection: key, label: recordLabel(record), action: before ? '更新' : '新增', time })
      }
    }
    for (const record of previous.values()) {
      changes.push({ collection: key, label: recordLabel(record), action: '删除', time })
    }
    if (!changes.length) return
    demoActivity.value = [...changes, ...demoActivity.value].slice(0, 30)
    try {
      localStorage.setItem(activityKey, JSON.stringify(demoActivity.value))
    } catch {
      // The underlying change was saved successfully even if the activity cannot be persisted.
    }
  }

  return { records, replace }
}

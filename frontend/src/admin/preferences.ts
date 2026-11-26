import { readonly, shallowRef } from 'vue'
import { adminRequest, isAdminDemo } from '../api/admin/client'

export const adminPageSizes = [20, 50, 100] as const
export const adminDashboardPeriods = [7, 30, 90] as const

export interface AdminPreferences {
  sidebarCollapsed: boolean
  pageSize: typeof adminPageSizes[number]
  dashboardPeriod: typeof adminDashboardPeriods[number]
}

export const defaultAdminPreferences: Readonly<AdminPreferences> = Object.freeze({
  sidebarCollapsed: false,
  pageSize: 20,
  dashboardPeriod: 7,
})

const storageKey = 'gamewiki:admin:preferences:v1'

function readAdminPreferences(): AdminPreferences {
  try {
    const stored = JSON.parse(window.localStorage.getItem(storageKey) ?? 'null') as Partial<AdminPreferences> | null
    return {
      sidebarCollapsed: stored?.sidebarCollapsed === true,
      pageSize: adminPageSizes.find((size) => size === stored?.pageSize) ?? defaultAdminPreferences.pageSize,
      dashboardPeriod: adminDashboardPeriods.find((period) => period === stored?.dashboardPeriod)
        ?? defaultAdminPreferences.dashboardPeriod,
    }
  } catch {
    return { ...defaultAdminPreferences }
  }
}

const current = shallowRef(isAdminDemo ? readAdminPreferences() : { ...defaultAdminPreferences })
export const adminPreferences = readonly(current)

export async function loadAdminPreferences() {
  if (!isAdminDemo) current.value = await adminRequest<AdminPreferences>({ url: '/preferences' })
}

export async function saveAdminPreferences(preferences: AdminPreferences) {
  if (isAdminDemo) {
    const next = { ...preferences }
    window.localStorage.setItem(storageKey, JSON.stringify(next))
    current.value = next
  } else {
    current.value = await adminRequest<AdminPreferences>({ url: '/preferences', method: 'PUT', data: preferences })
  }
}

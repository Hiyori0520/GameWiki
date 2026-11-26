import { BookOutlined, HomeOutlined, TeamOutlined } from '@ant-design/icons-vue'
import AdminGameIcon from '../components/admin/AdminGameIcon.vue'

export const adminModules = [
  { id: 'dashboard', label: '后台总览', path: '/admin/dashboard', icon: HomeOutlined },
  { id: 'games', label: '游戏管理', path: '/admin/games', icon: AdminGameIcon },
  { id: 'guides', label: '攻略管理', path: '/admin/guides', icon: BookOutlined },
  { id: 'users', label: '用户管理', path: '/admin/users', icon: TeamOutlined },
] as const

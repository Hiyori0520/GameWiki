import { computed } from 'vue'
import { covers } from './art'
import { createDemoCollection } from './storage'

export const gameStatusLabels = {
  draft: '草稿',
  published: '已发布',
  pending: '待审核',
  offline: '已下架',
} as const

export type GameStatus = keyof typeof gameStatusLabels

export interface GameRecord {
  id: number
  name: string
  alias: string
  company: string
  cover: string
  category: string
  tags: string[]
  platforms: string[]
  releaseDate: string
  rating: number | null
  votes: number
  status: GameStatus
  description: string
  createdAt: string
  originalName?: string
  englishName?: string
  aliases?: string[]
  developer?: string
  background?: string
  categories?: string[]
  deletedAt?: string
  version?: number
}

export const gameCategories = ['GAL / 视觉小说', 'ADV / 冒险', 'RPG / 角色扮演', 'SLG / 模拟经营', 'ACT / 动作', '其他',
  'ARPG / 动作角色扮演', 'FPS / 第一人称射击', 'TPS / 第三人称射击', 'RTS / 即时战略',
  'TBS / 回合制策略', 'SIM / 模拟', 'RAC / 竞速', 'SPG / 体育', 'FTG / 格斗',
  'RHY / 音乐节奏', 'PUZ / 解谜', '生存', '恐怖', '沙盒', 'MMO / 多人在线']
export const gamePlatforms = ['Windows', 'Nintendo Switch', 'PlayStation', 'macOS', 'Android', 'iOS', 'Linux', 'Xbox', '其他平台']

type GameSeed = Pick<GameRecord, 'name' | 'alias' | 'company' | 'tags' | 'platforms' | 'releaseDate' | 'rating' | 'votes'> & {
  categoryIndex: number
  coverIndex?: number
  status?: GameStatus
}

const seeds: GameSeed[] = import.meta.env.DEV ? [
  { name: '白色相簿2', alias: 'WHITE ALBUM2', company: 'Leaf', tags: ['恋爱', '校园', '音乐'], platforms: ['Windows'], releaseDate: '2010-03-26', rating: 9.8, votes: 12461, categoryIndex: 0, coverIndex: 0 },
  { name: 'clannad', alias: 'CLANNAD', company: 'Visual Arts / Key', tags: ['恋爱', '催泪', '日常'], platforms: ['Windows', 'Nintendo Switch', 'PlayStation'], releaseDate: '2004-04-28', rating: 9.7, votes: 18392, categoryIndex: 0, coverIndex: 1 },
  { name: '魔法使之夜', alias: '魔法使いの夜', company: 'TYPE-MOON', tags: ['奇幻', '悬疑', '剧情'], platforms: ['Windows'], releaseDate: '2012-04-12', rating: 9.6, votes: 7893, categoryIndex: 0, coverIndex: 2 },
  { name: '苍之彼方的四重奏', alias: '蒼の彼方のフォーリズム', company: 'sprite', tags: ['校园', '运动', '恋爱'], platforms: ['Windows'], releaseDate: '2014-11-28', rating: 9.1, votes: 6420, categoryIndex: 0, coverIndex: 3 },
  { name: '千恋＊万花', alias: '千恋＊万花', company: 'Yuzusoft', tags: ['恋爱', '和风', '日常'], platforms: ['Windows'], releaseDate: '2016-07-29', rating: 9.4, votes: 8115, categoryIndex: 0, coverIndex: 4 },
  { name: 'summer pockets', alias: 'Summer Pockets', company: 'Visual Arts / Key', tags: ['恋爱', '夏日', '剧情'], platforms: ['Windows', 'Nintendo Switch'], releaseDate: '2018-06-29', rating: 9.2, votes: 3798, categoryIndex: 0, coverIndex: 5 },
  { name: '星之终途', alias: '終のステラ', company: 'Visual Arts / Key', tags: ['科幻', '旅行'], platforms: ['Windows', 'Nintendo Switch'], releaseDate: '2022-09-30', rating: null, votes: 0, categoryIndex: 0, status: 'pending' },
  { name: 'GRIS', alias: '', company: 'Devolver Digital', tags: ['解谜', '治愈'], platforms: ['Windows', 'Nintendo Switch', 'PlayStation', 'macOS'], releaseDate: '2018-12-13', rating: 9.0, votes: 1265, categoryIndex: 1 },
  { name: '空洞骑士', alias: 'Hollow Knight', company: 'Team Cherry', tags: ['探索', '奇幻'], platforms: ['Windows', 'Nintendo Switch', 'PlayStation'], releaseDate: '2017-02-24', rating: 9.5, votes: 5208, categoryIndex: 4 },
  { name: '星露谷物语', alias: 'Stardew Valley', company: 'ConcernedApe', tags: ['种植', '经营'], platforms: ['Windows', 'Nintendo Switch', 'PlayStation', 'Android', 'iOS', 'Linux', 'Xbox', '其他平台'], releaseDate: '2016-02-26', rating: 9.6, votes: 9206, categoryIndex: 3 },
  { name: '逆转裁判123', alias: 'Phoenix Wright: Ace Attorney Trilogy', company: 'Capcom', tags: ['推理', '悬疑'], platforms: ['Windows', 'Nintendo Switch', 'PlayStation'], releaseDate: '2019-02-21', rating: 9.1, votes: 1890, categoryIndex: 1, status: 'offline' },
  { name: '女神异闻录5 皇家版', alias: 'Persona 5 Royal', company: 'ATLUS', tags: ['回合制', '校园'], platforms: ['Windows', 'Nintendo Switch', 'PlayStation'], releaseDate: '2019-10-31', rating: null, votes: 0, categoryIndex: 2, status: 'pending' },
] : []

const seedGames: GameRecord[] = seeds.map(({ categoryIndex, coverIndex, status, ...seed }, index) => ({
  ...seed,
  id: index + 1,
  cover: coverIndex === undefined ? '' : covers[coverIndex] ?? '',
  category: gameCategories[categoryIndex] ?? '其他',
  status: status ?? 'published',
  description: '',
  createdAt: new Date(Date.UTC(2026, 10, 17 - index, 12)).toISOString(),
}))

const demoGames = createDemoCollection<GameRecord>('games', seedGames)
export const games = {
  records: computed(() => demoGames.records.value.filter((record) => !record.deletedAt)),
  allRecords: demoGames.records,
  replace: demoGames.replace,
}

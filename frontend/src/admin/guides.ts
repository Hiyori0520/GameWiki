import { avatars, covers } from './art'
import { createDemoCollection } from './storage'
import type { GuideRecord, GuideTaxonomyItem } from '../types/admin/guide'

export { formatGuideCount, guideStatusLabels, type GuideRecord, type GuideStatus } from '../types/admin/guide'

const categories = import.meta.env.DEV ? [
  '主线攻略', '支线任务', 'Boss 攻略', '角色攻略', '装备攻略', '地图攻略', '收集攻略', '成就攻略',
  '新手指南', '系统解析', '建筑与经营', '多人玩法', '剧情分支', '其他攻略',
] : []
const tags = import.meta.env.DEV ? [
  '全路线', '角色线', '结局', 'CG 收集', '成就', '系统解析', '新手向', '全收集',
  '选项分支', '多周目', '真结局', '隐藏要素', 'DLC', '中文汉化', '补丁', '关键选项', '基础入门', '音乐',
  '高难度', '速通', '无伤', 'Boss 战', '装备搭配', '技能加点', '联机', '剧透', '版本更新',
] : []

const gameNames = {
  1: '白色相簿2',
  2: 'clannad',
  3: '魔法使之夜',
  4: '苍之彼方的四重奏',
  5: '千恋＊万花',
  6: 'Summer Pockets',
  7: '星之终途',
  9: '空洞骑士',
  10: '星露谷物语',
  11: '逆转裁判123',
} as const

const authorPortraits = {
  雪之下: avatars[0], 风见: avatars[2], 青空: avatars[3], 樱小路: avatars[4], 羽音: avatars[5],
} as const

type SeedGuide = Pick<GuideRecord, 'id' | 'title' | 'description' | 'category' | 'tags' | 'createdAt' | 'status'> & {
  gameId: keyof typeof gameNames
  author: keyof typeof authorPortraits
  image: number
  views?: number
  likes?: number
}

function seedGuide({ image, views = 0, likes = 0, ...guide }: SeedGuide): GuideRecord {
  return {
    ...guide,
    category: guide.category === '流程攻略' ? '主线攻略' : guide.category,
    gameName: gameNames[guide.gameId],
    avatar: authorPortraits[guide.author],
    cover: covers[image] ?? '',
    body: `## ${guide.title}\n\n${guide.description}`,
    featured: guide.id === 1,
    views,
    likes,
    publishedAt: guide.status === 'published' || guide.status === 'offline' ? guide.createdAt : null,
  }
}

const seed: GuideRecord[] = import.meta.env.DEV ? [
  seedGuide({
    id: 1, gameId: 1, title: '【全路线攻略】白色相簿2', description: '全周目流程与结局达成指南',
    category: '流程攻略', tags: ['全路线', '结局'], author: '雪之下', image: 2,
    createdAt: '2026-11-17T20:46:00', status: 'published', views: 1284000, likes: 36000,
  }),
  seedGuide({
    id: 2, gameId: 2, title: '【角色攻略】坂上智代', description: '个人线详细流程与选项解析',
    category: '角色攻略', tags: ['角色线', '关键选项'], author: '风见', image: 1,
    createdAt: '2026-11-16T18:32:00', status: 'published', views: 862000, likes: 21000,
  }),
  seedGuide({
    id: 3, gameId: 3, title: '【新手向】魔法使之夜', description: '入门指南与系统详解',
    category: '新手指南', tags: ['系统解析', '基础入门'], author: '青空', image: 3,
    createdAt: '2026-11-15T21:07:00', status: 'published', views: 428000, likes: 12000,
  }),
  seedGuide({
    id: 4, gameId: 5, title: '【全收集】千恋＊万花', description: 'CG・音乐・场景全收集攻略',
    category: '收集攻略', tags: ['全收集', 'CG 收集', '音乐'], author: '樱小路', image: 4,
    createdAt: '2026-11-14T19:20:00', status: 'published', views: 576000, likes: 19000,
  }),
  seedGuide({
    id: 5, gameId: 4, title: '【成就指南】苍之彼方的四重奏', description: '全成就解锁条件一览',
    category: '成就攻略', tags: ['成就', '全收集'], author: '羽音', image: 2,
    createdAt: '2026-11-13T17:55:00', status: 'draft', views: 213000, likes: 7842,
  }),
  seedGuide({
    id: 6, gameId: 7, title: '星之终途通关与剧情分支', description: '按章节整理关键选择与剧情回收路线',
    category: '流程攻略', tags: ['全路线', '选项分支'], author: '青空', image: 5,
    createdAt: '2026-11-12T16:05:00', status: 'pending',
  }),
  seedGuide({
    id: 7, gameId: 6, title: 'Summer Pockets 初次游玩指南', description: '开局准备、路线分歧和存档建议',
    category: '新手指南', tags: ['新手向', '角色线'], author: '樱小路', image: 0,
    createdAt: '2026-11-12T12:30:00', status: 'pending',
  }),
  seedGuide({
    id: 8, gameId: 1, title: '《白色相簿2》最终章补充路线', description: '最终章分歧节点与真结局条件',
    category: '流程攻略', tags: ['真结局', '多周目'], author: '雪之下', image: 0,
    createdAt: '2026-11-10T09:10:00', status: 'published', views: 33400, likes: 1500,
  }),
  seedGuide({
    id: 9, gameId: 5, title: '千恋＊万花共通线选择整理', description: '整理角色分歧前的公共路线选项',
    category: '角色攻略', tags: ['角色线', '选项分支'], author: '风见', image: 4,
    createdAt: '2026-11-08T18:20:00', status: 'published', views: 17700, likes: 917,
  }),
  seedGuide({
    id: 10, gameId: 2, title: 'CLANNAD 旧版汉化补丁说明', description: '旧补丁版本说明和常见问题',
    category: '其他攻略', tags: ['中文汉化', '补丁'], author: '羽音', image: 1,
    createdAt: '2026-11-07T14:30:00', status: 'offline', views: 7900, likes: 138,
  }),
  seedGuide({
    id: 11, gameId: 9, title: '《空洞骑士》前期护符搭配', description: '开荒常用护符与探索路线',
    category: '系统解析', tags: ['系统解析', '新手向'], author: '青空', image: 5,
    createdAt: '2026-11-06T11:42:00', status: 'published', views: 60400, likes: 2710,
  }),
  seedGuide({
    id: 12, gameId: 10, title: '《星露谷物语》春季作物手册', description: '按收获周期整理首年春季作物',
    category: '系统解析', tags: ['系统解析', '基础入门'], author: '羽音', image: 3,
    createdAt: '2026-11-02T10:30:00', status: 'draft',
  }),
  seedGuide({
    id: 13, gameId: 3, title: '魔法使之夜番外篇开放条件', description: '番外篇开放时点和额外内容说明',
    category: '收集攻略', tags: ['隐藏要素', '全收集'], author: '雪之下', image: 2,
    createdAt: '2026-10-31T17:25:00', status: 'pending',
  }),
  seedGuide({
    id: 14, gameId: 11, title: '逆转裁判123 第一章证物笔记', description: '按庭审环节归纳证词与出示证物',
    category: '流程攻略', tags: ['选项分支', '新手向'], author: '风见', image: 5,
    createdAt: '2026-10-27T20:07:00', status: 'published', views: 21400, likes: 776,
  }),
  seedGuide({
    id: 15, gameId: 2, title: 'CLANNAD 全成就补充资料', description: '全成就路线与触发条件',
    category: '成就攻略', tags: ['全收集', '成就'], author: '樱小路', image: 1,
    createdAt: '2026-10-25T12:10:00', status: 'rejected',
  }),
] : []

export const guides = createDemoCollection('guides', seed)
export const guideCategories = createDemoCollection<GuideTaxonomyItem>('guide-categories', categories.map((name, index) => ({ id: index + 1, name })))
export const guideTags = createDemoCollection<GuideTaxonomyItem>('guide-tags', tags.map((name, index) => ({ id: index + 1, name })))

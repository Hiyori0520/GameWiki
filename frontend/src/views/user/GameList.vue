<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined, BarsOutlined, BlockOutlined, CalendarOutlined, CheckOutlined,
  DownOutlined, EllipsisOutlined, FileTextOutlined, SearchOutlined, SortAscendingOutlined,
  StarOutlined, TagOutlined,
} from '@ant-design/icons-vue'
import UserNav from '../../components/UserNav.vue'
import GameIcon from '../../components/admin/AdminGameIcon.vue'
import GameLibraryCard from '../../components/GameLibraryCard.vue'
import { catalogApi, gameCard } from '../../api/catalog'

const all = ''
const pageSize = 6
const featuredTags = ['剧情', '奇幻', '探索', '模拟']
const sortOptions = [
  { value: 'createdAt', label: '最新收录', icon: CalendarOutlined },
  { value: 'rating', label: '最高评分', icon: StarOutlined },
  { value: 'guides', label: '最多攻略', icon: FileTextOutlined },
  { value: 'name', label: '游戏名称', icon: SortAscendingOutlined },
]
const viewOptions = [
  { value: 'grid', label: '网格视图', icon: AppstoreOutlined },
  { value: 'list', label: '列表视图', icon: BarsOutlined },
]

const route = useRoute()
const router = useRouter()
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const category = ref(all)
const selectedTag = ref(all)
const sortBy = ref('createdAt')
const view = ref('grid')
const currentPage = ref(1)
const gameCount = ref(0)
const catalogCount = ref<number>()
const guideCount = ref<number>()
const pageGames = ref<ReturnType<typeof gameCard>[]>([])
const categories = ref<string[]>([])
const tags = ref<string[]>([])
const optionsLoaded = ref(false)
const optionsError = ref(false)
const moreTagsOpen = ref(false)
const favoriteIds = ref<Set<number>>()
const savingFavorites = ref(new Set<number>())
const favoritesError = ref(false)
const loading = ref(true)
const loadError = ref(false)
const reload = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(gameCount.value / pageSize)))
const pages = computed(() => {
  const first = Math.max(1, Math.min(currentPage.value - 2, totalPages.value - 4))
  return Array.from({ length: Math.min(5, totalPages.value) }, (_, index) => first + index)
})
const orderedTags = computed(() => [...new Set([...featuredTags.filter(tag => tags.value.includes(tag)), ...tags.value])])
const quickTags = computed(() => orderedTags.value.slice(0, 4))
const extraTags = computed(() => orderedTags.value.slice(4))
const extraTagSelected = computed(() => extraTags.value.includes(selectedTag.value))
const hasFilters = computed(() => Boolean(keyword.value.trim() || category.value || selectedTag.value))
const stats = computed(() => [
  { label: '收录游戏', count: catalogCount.value, icon: GameIcon },
  { label: '游戏类型', count: optionsLoaded.value ? categories.value.length : undefined, icon: BlockOutlined },
  { label: '常用标签', count: optionsLoaded.value ? tags.value.length : undefined, icon: TagOutlined },
  { label: '攻略内容', count: guideCount.value, icon: FileTextOutlined },
])

async function loadOptions() {
  optionsError.value = false
  try {
    const options = await catalogApi.options()
    categories.value = options.categories
    tags.value = options.tags
    optionsLoaded.value = true
  } catch {
    optionsError.value = true
  }
}

async function loadFavorites() {
  favoritesError.value = false
  try {
    const favorites = await catalogApi.favorites()
    favoriteIds.value = new Set(favorites.games.map(game => game.id))
  } catch {
    favoritesError.value = true
  }
}

onMounted(() => {
  void loadOptions()
  void loadFavorites()
  void catalogApi.games({ pageSize: 1 }).then(data => { catalogCount.value ??= data.total }).catch(() => {})
  void catalogApi.guides({ pageSize: 1 }).then(data => { guideCount.value = data.total }).catch(() => {})
})

watch([keyword, category, selectedTag, sortBy], () => { currentPage.value = 1 }, { flush: 'sync' })
watch(() => route.query.keyword, value => { keyword.value = typeof value === 'string' ? value : '' })

watch([keyword, category, selectedTag, sortBy, currentPage, reload], ([term, type, tag, sort, page], _, onCleanup) => {
  const controller = new AbortController()
  const searchTerm = term.trim()
  const timer = window.setTimeout(async () => {
    try {
      const data = await catalogApi.games({
        keyword: searchTerm,
        category: type,
        tag,
        sortBy: sort,
        sortOrder: sort === 'name' ? 'asc' : 'desc',
        page,
        pageSize,
      }, controller.signal)
      if (controller.signal.aborted) return
      if (!searchTerm && !type && !tag) catalogCount.value = data.total
      gameCount.value = data.total
      if (page > totalPages.value) currentPage.value = totalPages.value
      else pageGames.value = data.records.map(gameCard)
    } catch {
      if (!controller.signal.aborted) loadError.value = true
    } finally {
      if (!controller.signal.aborted) loading.value = false
    }
  }, 160)
  loading.value = true
  loadError.value = false
  onCleanup(() => { window.clearTimeout(timer); controller.abort() })
}, { immediate: true })

function search() {
  currentPage.value = 1
  const term = keyword.value.trim()
  reload.value++
  void router.replace({ path: '/games', query: term ? { keyword: term } : {} })
}

function selectTag(tag: string) {
  selectedTag.value = tag
  moreTagsOpen.value = false
}

function resetFilters() {
  keyword.value = ''
  category.value = all
  selectTag(all)
  sortBy.value = 'createdAt'
  search()
}

async function toggleFavorite(id: number) {
  const ids = favoriteIds.value
  if (!ids || savingFavorites.value.has(id)) return
  savingFavorites.value.add(id)
  try {
    const result = await catalogApi.favorite('game', id, !ids.has(id))
    if (result.active) ids.add(id)
    else ids.delete(id)
  } catch {
    // The request client displays the server error and the current state stays visible.
  } finally {
    savingFavorites.value.delete(id)
  }
}
</script>

<template>
  <div class="game-library">
    <UserNav class="library-nav" />

    <main>
      <header class="library-hero">
        <div class="library-container hero-content">
          <h1><GameIcon />游戏库</h1>
          <p>发现精彩游戏，探索丰富攻略知识。</p>
          <dl class="library-stats">
            <div v-for="stat in stats" :key="stat.label" class="library-stat">
              <component :is="stat.icon" aria-hidden="true" />
              <dt>{{ stat.label }}</dt>
              <dd>{{ stat.count?.toLocaleString('zh-CN') ?? '—' }}</dd>
            </div>
          </dl>
        </div>
      </header>

      <div class="library-body">
        <div class="library-container">
          <form class="filter-panel" aria-label="搜索和筛选游戏" @submit.prevent="search">
            <label class="search-field">
              <SearchOutlined aria-hidden="true" />
              <input v-model="keyword" type="search" aria-label="关键词" placeholder="搜索游戏名称、描述、关键词..." />
            </label>

            <label class="filter-group category-filter">
              <span class="filter-label"><BlockOutlined aria-hidden="true" />游戏类型</span>
              <span class="select-control">
                <select v-model="category" :title="category || '全部'">
                  <option :value="all">全部</option>
                  <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
                </select>
                <DownOutlined aria-hidden="true" />
              </span>
            </label>

            <div class="filter-group tag-filter" role="group" aria-label="游戏标签">
              <span class="filter-label"><TagOutlined aria-hidden="true" />游戏标签</span>
              <div class="tag-options">
                <button type="button" class="tag" :aria-pressed="selectedTag === all" @click="selectTag(all)">
                  <CheckOutlined v-if="selectedTag === all" aria-hidden="true" />全部
                </button>
                <button
                  v-for="tag in quickTags"
                  :key="tag"
                  type="button"
                  class="tag"
                  :aria-pressed="selectedTag === tag"
                  @click="selectTag(tag)"
                >{{ tag }}</button>
                <el-popover
                  v-if="extraTags.length"
                  v-model:visible="moreTagsOpen"
                  placement="bottom-end"
                  role="dialog"
                  trigger="click"
                  :teleported="false"
                  :width="248"
                  popper-class="game-library-tag-popup"
                >
                  <template #reference>
                    <button
                      type="button"
                      class="tag more-tags"
                      :class="{ selected: extraTagSelected }"
                      :title="extraTagSelected ? selectedTag : '更多标签'"
                      :aria-label="extraTagSelected ? '更多标签，当前为' + selectedTag : '更多标签'"
                    >
                      <span v-if="extraTagSelected">{{ selectedTag }}</span>
                      <EllipsisOutlined v-else aria-hidden="true" />
                    </button>
                  </template>
                  <div class="tag-popup" role="group" aria-label="更多游戏标签">
                    <button
                      v-for="tag in extraTags"
                      :key="tag"
                      type="button"
                      class="tag"
                      :aria-pressed="selectedTag === tag"
                      @click="selectTag(tag)"
                    >{{ tag }}</button>
                  </div>
                </el-popover>
                <button v-if="optionsError" type="button" class="tag" @click="loadOptions">重试加载筛选</button>
              </div>
            </div>

            <button type="submit" class="search-button"><SearchOutlined aria-hidden="true" />搜索游戏</button>
          </form>

          <section class="library-results" aria-labelledby="results-heading">
            <div class="list-toolbar">
              <div class="result-heading">
                <h2 id="results-heading"><GameIcon />{{ hasFilters ? '搜索结果' : '全部游戏' }}</h2>
                <p aria-live="polite">
                  {{ loading ? '正在加载游戏…' : loadError ? '游戏加载失败' : `共找到 ${gameCount} 款游戏` }}
                  <button v-if="hasFilters" type="button" class="text-button" @click="resetFilters">重置筛选</button>
                </p>
              </div>

              <div class="toolbar-controls">
                <div class="segmented" role="group" aria-label="游戏排序">
                  <button
                    v-for="option in sortOptions"
                    :key="option.value"
                    type="button"
                    :aria-pressed="sortBy === option.value"
                    @click="sortBy = option.value"
                  ><component :is="option.icon" aria-hidden="true" />{{ option.label }}</button>
                </div>
                <div class="segmented view-switch" role="group" aria-label="游戏视图">
                  <button
                    v-for="option in viewOptions"
                    :key="option.value"
                    type="button"
                    :aria-pressed="view === option.value"
                    :aria-label="option.label"
                    :title="option.label"
                    @click="view = option.value"
                  ><component :is="option.icon" aria-hidden="true" /></button>
                </div>
              </div>
            </div>

            <p v-if="favoritesError" class="favorite-error" role="status">
              收藏状态加载失败
              <button type="button" class="text-button" @click="loadFavorites">重试</button>
            </p>
            <div v-if="loading && !pageGames.length" class="empty-state" role="status">正在加载游戏…</div>
            <div v-else-if="loadError" class="empty-state" role="alert">
              <p>游戏库暂时无法加载。</p>
              <button type="button" class="text-button" @click="reload++">重新加载</button>
            </div>
            <div v-else-if="pageGames.length" class="game-results" :class="{ 'list-mode': view === 'list' }" :aria-busy="loading">
              <GameLibraryCard
                v-for="game in pageGames"
                :key="game.id"
                :game="game"
                :list="view === 'list'"
                :collected="favoriteIds?.has(game.id) ?? false"
                :favorite-disabled="!favoriteIds || savingFavorites.has(game.id)"
                @favorite="toggleFavorite(game.id)"
              />
            </div>
            <div v-else class="empty-state">
              <h3>没有找到相关游戏</h3>
              <p>请尝试其他关键词或重置筛选条件。</p>
              <button type="button" class="text-button" @click="resetFilters">重置筛选</button>
            </div>

            <nav v-if="totalPages > 1 && !loadError" class="pagination" aria-label="游戏库分页">
              <button type="button" :disabled="currentPage === 1" @click="currentPage--">上一页</button>
              <button
                v-for="page in pages"
                :key="page"
                type="button"
                :aria-current="currentPage === page ? 'page' : undefined"
                :aria-label="'第 ' + page + ' 页'"
                @click="currentPage = page"
              >{{ page }}</button>
              <button type="button" :disabled="currentPage >= totalPages" @click="currentPage++">下一页</button>
            </nav>
          </section>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped src="./game-library.css"></style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRightOutlined, FileTextOutlined, StarFilled, StarOutlined } from '@ant-design/icons-vue'
import type { gameCard } from '../api/catalog'
import blossom from '../assets/library/blossom.webp'
import starlight from '../assets/library/starlight.webp'
import skyline from '../assets/library/skyline.webp'

const props = defineProps<{
  game: ReturnType<typeof gameCard>
  list: boolean
  collected: boolean
  favoriteDisabled: boolean
}>()
defineEmits<{ favorite: [] }>()

const covers = new Map([['SLG', blossom], ['ACT', starlight], ['ADV', skyline]])
const failedImage = ref('')
const categoryCode = computed(() => props.game.category.split('/')[0].trim())
const cover = computed(() => {
  const image = props.game.image
  return image && image !== failedImage.value ? image : (covers.get(categoryCode.value) ?? starlight)
})
</script>

<template>
  <article class="game-card" :class="{ 'list-card': list }">
    <RouterLink class="game-link" :to="{ name: 'GameDetail', params: { id: game.id } }" :aria-label="'查看详情：' + game.name">
      <div class="game-cover">
        <img :src="cover" alt="" decoding="async" @error="failedImage = cover" />
        <span class="category-badge" :title="game.category">{{ categoryCode }}</span>
      </div>
      <div class="game-info">
        <h3>{{ game.name }}</h3>
        <p class="description">{{ game.description }}</p>
        <div class="game-tags">
          <span v-for="tag in game.tags" :key="tag">{{ tag }}</span>
        </div>
        <div class="card-footer">
          <span><FileTextOutlined aria-hidden="true" />{{ game.guideCount }} 篇攻略</span>
          <span><StarFilled aria-hidden="true" />{{ game.rating?.toFixed(1) ?? '暂无评分' }}</span>
          <span class="detail-link">查看详情 <ArrowRightOutlined aria-hidden="true" /></span>
        </div>
      </div>
    </RouterLink>
    <button
      type="button"
      class="favorite"
      :disabled="favoriteDisabled"
      :aria-pressed="collected"
      :aria-label="(collected ? '取消收藏：' : '收藏：') + game.name"
      :title="collected ? '取消收藏' : '收藏游戏'"
      @click="$emit('favorite')"
    >
      <StarFilled v-if="collected" aria-hidden="true" />
      <StarOutlined v-else aria-hidden="true" />
    </button>
  </article>
</template>

<style scoped>
.game-card {
  position: relative;
  min-width: 0;
  display: grid;
  border: 1px solid transparent;
  border-radius: 16px;
  background:
    linear-gradient(155deg, #172443, #1b2b4e) padding-box,
    linear-gradient(132deg, #b9a0ff, #5b6a9a 52%, #6f8df1) border-box;
  transition: box-shadow .18s, transform .18s;
}

.game-card:hover,
.game-card:focus-within {
  transform: translateY(-2px);
  box-shadow: 0 5px 26px #5272f34a;
}

.game-link {
  display: grid;
  min-width: 0;
  overflow: hidden;
  border-radius: inherit;
  color: inherit;
  text-decoration: none;
}

.game-cover {
  position: relative;
  height: clamp(108px, 8vw, 134px);
  overflow: hidden;
  background: #15244e;
}

.game-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 47%;
}

.game-cover::after {
  position: absolute;
  inset: 65% 0 0;
  background: linear-gradient(transparent, #192745);
  content: '';
}

.category-badge {
  position: absolute;
  top: 9px;
  left: 12px;
  z-index: 1;
  max-width: calc(100% - 60px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid #8992e78c;
  border-radius: 20px;
  padding: 1px 12px;
  background: #344585e8;
  color: #faf8ff;
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
}

.favorite {
  position: absolute;
  top: 5px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #1520427a;
  color: #f4f5ff;
  font-size: 18px;
  cursor: pointer;
}

.favorite:hover:enabled { background: #2d3d75; }
.favorite[aria-pressed="true"] { color: #ffd477; }
.favorite:disabled { opacity: .55; cursor: wait; }
.favorite:focus-visible, .game-link:focus-visible { outline: 2px solid #cdc3ff; outline-offset: 2px; }

.game-info {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 0 clamp(12px, 1.45vw, 22px) 10px;
}

.game-info h3,
.description {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.game-info h3 { font-size: 18px; line-height: 24px; }

.description {
  min-height: 17px;
  color: #c0c9dd;
  font-size: 12px;
  line-height: 17px;
}

.game-tags {
  display: flex;
  gap: 6px;
  min-height: 20px;
  overflow: hidden;
}

.game-tags span {
  flex: none;
  padding: 1px 12px;
  border-radius: 20px;
  background: #2a3d62;
  color: #e1e8fa;
  font-size: 12px;
  line-height: 18px;
}

.card-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px clamp(10px, 2.1vw, 32px);
  min-height: 18px;
  padding-top: 7px;
  border-top: 1px solid #7996e039;
  font-size: 12px;
}

.card-footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.detail-link { margin-left: auto; color: #aec5ff; font-weight: 600; }

.list-card .game-link { grid-template-columns: minmax(124px, 22%) minmax(0, 1fr); }
.list-card .game-cover { height: 100%; min-height: 114px; }
.list-card .game-cover::after { content: none; }
.list-card .game-info { padding-top: 9px; }
.list-card h3 { padding-right: 28px; }

@media (max-width: 520px) {
  .list-card .game-link { grid-template-columns: 98px minmax(0, 1fr); }
  .list-card .game-info { padding-inline: 10px; }
  .list-card .detail-link { margin-left: 0; }
}
</style>

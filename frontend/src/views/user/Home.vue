
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserNav from '../../components/UserNav.vue'
import { catalogApi, gameCard, type CatalogGuide } from '../../api/catalog'

const router = useRouter()
const games = ref<ReturnType<typeof gameCard>[]>([])
const guides = ref<CatalogGuide[]>([])
const loadError = ref(false)

onMounted(async () => {
  try {
    const [gameList, guideList] = await Promise.all([
      catalogApi.games({ pageSize: 6, sortBy: 'guides' }),
      catalogApi.guides({ pageSize: 5 }),
    ])
    games.value = gameList.records.map(gameCard)
    guides.value = guideList.records
  } catch { loadError.value = true }
})
</script>

<template>
  <div class="home">
    <UserNav show-search />

    <main class="home-content">
      <!-- 首页主视觉 -->
      <section class="hero">
        <div class="hero-content">
          <span class="eyebrow">GAME KNOWLEDGE BASE · AI POWERED</span>

          <h1>
            探索游戏世界<br />
            <span>让每一次冒险都有答案</span>
          </h1>

          <p>
            汇聚丰富的游戏资料与实用攻略，
            结合AI智能知识检索，让游戏探索更加轻松。
          </p>

          <div class="hero-actions">
            <button class="primary-btn" @click="router.push('/games')">
              探索游戏库
            </button>

            <button class="outline-btn" @click="router.push('/ai/chat')">
              体验AI攻略助手
            </button>
          </div>
        </div>

        <div class="hero-art">
          <div class="glow-ring">
            <div class="glow-core">G</div>
          </div>
          <div class="hero-art-label">GAMEWIKI AI</div>
        </div>
      </section>

      <!-- 热门游戏 -->
      <section>
        <div class="section-heading">
          <div>
            <span class="section-label">GAME LIBRARY</span>
            <h2>热门游戏</h2>
            <p>探索当前热门游戏与攻略资料</p>
          </div>

          <RouterLink to="/games">查看全部 →</RouterLink>
        </div>

        <p v-if="loadError" role="alert">游戏库暂时无法加载，请稍后刷新。</p>
        <div class="game-grid">
          <div
            v-for="game in games"
            :key="game.id"
            class="game-card"
            @click="router.push(`/games/${game.id}`)"
          >
            <div class="game-cover" :class="game.cover" :style="game.image ? { backgroundImage: 'linear-gradient(0deg,#151529aa,transparent), url(' + JSON.stringify(game.image) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : undefined">
              <div class="game-symbol">{{ game.symbol }}</div>
              <span class="cover-name">{{ game.name }}</span>
            </div>

            <div class="game-info">
              <h3>{{ game.name }}</h3>
              <p>{{ game.category }}</p>
              <div class="game-meta">
                <span>{{ game.guideCount }} 篇攻略</span>
                <span>查看详情 →</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 最新攻略 -->
      <section>
        <div class="section-heading">
          <div>
            <span class="section-label">LATEST GUIDES</span>
            <h2>最新游戏攻略</h2>
            <p>发现实用的游戏技巧与探索指南</p>
          </div>
        </div>

        <div class="guide-list">
          <div
            v-for="guide in guides"
            :key="guide.id"
            class="guide-item"
            @click="router.push(`/guides/${guide.id}`)"
          >
            <div class="guide-icon">▤</div>

            <div class="guide-content">
              <span class="guide-category">{{ guide.category }}</span>
              <h3>{{ guide.title }}</h3>
              <p>
                {{ guide.gameName }}
                <span class="dot">·</span>
                {{ guide.views }} 次浏览
              </p>
            </div>

            <div class="guide-arrow">→</div>
          </div>
        </div>
      </section>

      <!-- AI助手推荐区域 -->
      <section class="ai-section">
        <div>
          <span class="section-label">AI GAME ASSISTANT</span>
          <h2>遇到游戏难题？问问AI助手</h2>
          <p>
            基于游戏攻略知识库，智能检索相关资料，
            帮助你快速找到游戏问题的答案。
          </p>

          <button class="primary-btn" @click="router.push('/ai/chat')">
            开始AI问答 →
          </button>
        </div>

        <div class="ai-preview">
          <div class="chat-question">
            如何找到游戏中的隐藏区域？
          </div>
          <div class="chat-answer">
            AI助手<br />
            我可以从攻略知识库中查找相关资料，
            为你整理探索步骤与注意事项。
          </div>
        </div>
      </section>
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <span>GameWiki AI · 智能游戏攻略知识库</span>
      <span>基于 Vue 3 + Spring Boot + Spring AI</span>
    </footer>

  </div>
</template>

<style scoped>
.home {
  --home-gutter: clamp(12px, 3.2vw, 64px);
  color: var(--gw-text);
  background: var(--gw-bg);
  min-height: 100vh;
}

.home-content,
.footer {
  width: min(100% - 2 * var(--home-gutter), 1580px);
  margin-inline: auto;
}

.home-content {
  display: grid;
  gap: clamp(32px, 5vw, 64px);
  padding-top: clamp(16px, 2.6vw, 32px);
}

.hero {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  align-items: center;
  box-sizing: border-box;
  min-height: 360px;
  padding: clamp(24px, 4vw, 48px);
  border: 1px solid var(--gw-border);
  border-radius: 20px;
  background:
    radial-gradient(ellipse at 85% 20%, #294780 0%, transparent 50%),
    linear-gradient(125deg, var(--gw-surface), var(--gw-nav));
  overflow: hidden;
}

.eyebrow,
.section-label {
  color: var(--gw-link);
  font-size: 12px;
  letter-spacing: 2px;
}

.hero h1 {
  font-size: clamp(30px, 3vw, 44px);
  line-height: 1.4;
  margin: 20px 0;
}

.hero h1 span {
  color: var(--gw-accent);
}

.hero p,
.section-heading p,
.ai-section p {
  color: var(--gw-muted);
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 30px;
}

button {
  cursor: pointer;
  border: none;
  font-size: 14px;
}

.primary-btn {
  padding: 12px 24px;
  color: white;
  border-radius: 9px;
  background: var(--gw-action-gradient);
}

.primary-btn:hover {
  background: var(--gw-primary-hover);
}

.home-content :is(a, button):focus-visible {
  outline: 2px solid var(--gw-accent);
  outline-offset: 3px;
}

.outline-btn {
  padding: 12px 24px;
  color: var(--gw-text);
  background: transparent;
  border: 1px solid var(--gw-border);
  border-radius: 9px;
}

.outline-btn:hover {
  border-color: var(--gw-accent);
}

.hero-art {
  text-align: center;
  position: relative;
}

.glow-ring {
  width: 230px;
  height: 230px;
  margin: auto;
  border: 2px solid #6687ee;
  border-radius: 50%;
  box-shadow: 0 0 90px #536ef355;
  display: flex;
  align-items: center;
  justify-content: center;
}

.glow-core {
  font-size: 110px;
  font-weight: 800;
  color: #a7beff;
  text-shadow: 0 0 50px #668aff;
}

.hero-art-label {
  margin-top: 25px;
  color: var(--gw-muted);
  letter-spacing: 5px;
  font-size: 12px;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 12px;
  margin-bottom: 18px;
}

.section-heading h2 {
  font-size: 27px;
  margin: 10px 0;
}

.section-heading p {
  margin: 0;
}

.section-heading a {
  color: var(--gw-link);
  text-decoration: none;
  white-space: nowrap;
}

.section-heading a:hover {
  text-decoration: underline;
}

.game-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.game-card {
  border-radius: 14px;
  overflow: hidden;
  background: var(--gw-surface);
  border: 1px solid var(--gw-border);
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;
}

.game-card:hover {
  transform: translateY(-5px);
  border-color: var(--gw-accent);
}

.game-cover {
  height: 225px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  position: relative;
}

.cover-blue {
  background: linear-gradient(145deg, #162e63, #4b70bd, #131b38);
}

.cover-purple {
  background: linear-gradient(145deg, #36214e, #855cbd, #16142d);
}

.cover-orange {
  background: linear-gradient(145deg, #5c241c, #b65e41, #251625);
}

.cover-teal {
  background: linear-gradient(145deg, #0a464d, #278c9b, #102737);
}

.game-symbol {
  font-size: 80px;
  color: #ffffffa0;
}

.cover-name {
  position: absolute;
  bottom: 20px;
  font-size: 19px;
  font-weight: bold;
}

.game-info {
  padding: 18px;
}

.game-info h3 {
  margin: 0;
}

.game-info p {
  color: var(--gw-muted);
  font-size: 13px;
}

.game-meta {
  display: flex;
  justify-content: space-between;
  color: var(--gw-link);
  font-size: 12px;
  margin-top: 18px;
}

.guide-list {
  display: grid;
  gap: 12px;
}

.guide-item {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 20px;
  border-radius: 12px;
  border: 1px solid var(--gw-border);
  background: var(--gw-surface);
  cursor: pointer;
}

.guide-item:hover {
  border-color: var(--gw-accent);
}

.guide-icon {
  background: var(--gw-surface-raised);
  border-radius: 12px;
  width: 65px;
  height: 65px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gw-link);
  font-size: 32px;
  flex-shrink: 0;
}

.guide-content {
  flex: 1;
}

.guide-category {
  font-size: 12px;
  color: var(--gw-link);
}

.guide-content h3 {
  margin: 8px 0;
  font-size: 16px;
}

.guide-content p {
  color: var(--gw-muted);
  font-size: 12px;
  margin: 0;
}

.dot {
  margin: 0 8px;
}

.guide-arrow {
  color: var(--gw-link);
  font-size: 24px;
}

.ai-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 45px;
  align-items: center;
  border-radius: 20px;
  padding: 45px;
  background: linear-gradient(120deg, var(--gw-surface), var(--gw-surface-raised));
  border: 1px solid var(--gw-border);
}

.ai-section h2 {
  font-size: 27px;
}

.ai-section button {
  margin-top: 20px;
}

.ai-preview {
  border-radius: 14px;
  background: var(--gw-nav);
  padding: 24px;
}

.chat-question {
  margin-left: auto;
  width: fit-content;
  max-width: 85%;
  background: var(--gw-surface-raised);
  padding: 14px;
  border-radius: 12px;
  font-size: 13px;
}

.chat-answer {
  box-sizing: border-box;
  width: 90%;
  margin-top: 20px;
  padding: 18px;
  border-radius: 12px;
  background: var(--gw-surface);
  color: var(--gw-muted);
  line-height: 1.9;
  font-size: 13px;
}

.footer {
  margin-top: clamp(32px, 5vw, 56px);
  padding: 25px 0;
  border-top: 1px solid var(--gw-border);
  color: var(--gw-muted);
  font-size: 12px;
  display: flex;
  justify-content: space-between;
}

@media (max-width: 800px) {
  .hero,
  .ai-section {
    grid-template-columns: 1fr;
    padding: 28px;
  }

  .hero {
    min-height: auto;
  }

  .hero-art {
    display: none;
  }

  .game-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .footer {
    flex-direction: column;
    gap: 10px;
  }
}

@media (max-width: 480px) {
  .hero,
  .ai-section {
    padding: 20px;
  }

  .ai-preview {
    padding: 16px;
  }

  .game-grid {
    grid-template-columns: 1fr;
  }

  .guide-icon {
    display: none;
  }
}
</style>

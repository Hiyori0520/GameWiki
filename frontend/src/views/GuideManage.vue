<template>
  <el-card shadow="never">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <div style="display: flex; gap: 12px">
        <el-input v-model="query.keyword" placeholder="搜索攻略标题或唯一编号" clearable style="width: 260px" @keyup.enter="loadList" />
        <el-select v-model="query.auditStatus" placeholder="审核状态" clearable style="width: 140px" @change="loadList">
          <el-option label="已上架" :value="1" />
          <el-option label="待审核" :value="0" />
        </el-select>
        <el-button type="primary" icon="Search" @click="loadList">检索</el-button>
      </div>
      <el-button type="success" icon="Plus" @click="openAddDialog">发布新攻略（Redis生成编号）</el-button>
    </div>

    <el-table :data="guideList" border stripe>
      <el-table-column prop="guideCode" label="唯一编号(Redis INCR)" width="180" />
      <el-table-column prop="title" label="攻略标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="categoryTag" label="战术标签" width="120">
        <template #default="{ row }"><el-tag>{{ row.categoryTag || '综合' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="auditStatus" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.auditStatus === 1 ? 'success' : 'warning'">
            {{ row.auditStatus === 1 ? '已上架' : '待审核' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="阅读量" width="90" align="center" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="viewDetailWithCache(row.id)">详情(走Redis缓存)</el-button>
          <el-button v-if="row.auditStatus === 0" size="small" type="success" @click="approveGuide(row)">审核上架</el-button>
          <el-button size="small" type="danger" link @click="deleteGuide(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addVisible" title="玩家投稿新攻略（自动生成唯一单号）" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属游戏">
          <el-select v-model="form.gameId" style="width: 100%">
            <el-option v-for="g in gameOptions" :key="g.id" :label="g.gameName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="攻略标题">
          <el-input v-model="form.title" placeholder="请输入攻略标题" />
        </el-form-item>
        <el-form-item label="战术标签">
          <el-input v-model="form.categoryTag" placeholder="例如：载具教学 / BOSS攻略 / 武器配装" />
        </el-form-item>
        <el-form-item label="核心摘要">
          <el-input v-model="form.summary" placeholder="一句话概括核心打法" />
        </el-form-item>
        <el-form-item label="攻略正文">
          <el-input v-model="form.contentMd" type="textarea" :rows="5" placeholder="支持 Markdown 格式战术详解..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGuide">提交入库</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="currentGuide.title" size="45%">
      <el-descriptions :column="2" border style="margin-bottom: 16px">
        <el-descriptions-item label="全局编号">{{ currentGuide.guideCode }}</el-descriptions-item>
        <el-descriptions-item label="战术分类">{{ currentGuide.categoryTag }}</el-descriptions-item>
        <el-descriptions-item label="缓存状态"><el-tag type="success">已写入 Redis 旁路缓存并累加热度</el-tag></el-descriptions-item>
      </el-descriptions>
      <div style="background: var(--gw-surface); padding: 16px; border-radius: 8px">
        <MarkdownView :content="currentGuide.contentMd" />
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'
import MarkdownView from '../components/MarkdownView.vue'

const guideList = ref([])
const gameOptions = ref([])
const query = reactive({ keyword: '', auditStatus: null })

const addVisible = ref(false)
const form = reactive({ gameId: 1, authorId: 1, title: '', categoryTag: '', summary: '', contentMd: '' })

const detailVisible = ref(false)
const currentGuide = ref({})

const loadList = async () => {
  const res = await request.get('/api/guide/all', { params: query })
  guideList.value = res.data
}

const loadGames = async () => {
  const res = await request.get('/api/basic/games')
  gameOptions.value = res.data
}

const openAddDialog = () => {
  Object.assign(form, { gameId: 1, authorId: 1, title: '', categoryTag: '', summary: '', contentMd: '' })
  addVisible.value = true
}

const submitGuide = async () => {
  const res = await request.post('/api/guide/submit', form)
  ElMessage.success(`投稿成功！Redis 生成唯一编号：${res.data.guideCode}`)
  addVisible.value = false
  loadList()
}

const viewDetailWithCache = async (id) => {
  const res = await request.get(`/api/guide/${id}`)
  if (!res.data) {
    ElMessage.warning('该攻略尚未审核上架，暂未进入前端缓存')
    return
  }
  currentGuide.value = res.data
  detailVisible.value = true
}

const approveGuide = async (row) => {
  await request.put('/api/guide/update', { id: row.id, auditStatus: 1 })
  ElMessage.success('审核上架成功，已同步触发缓存一致性刷新！')
  loadList()
}

const deleteGuide = (id) => {
  ElMessageBox.confirm('确定删除该篇攻略吗？', '提示', { type: 'warning' }).then(async () => {
    await request.delete(`/api/guide/${id}`)
    ElMessage.success('已删除')
    loadList()
  })
}

onMounted(() => {
  loadList()
  loadGames()
})
</script>
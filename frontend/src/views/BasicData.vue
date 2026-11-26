<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="武器/装备精确数值库（AI 检索源）" name="items">
        <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
          <el-input v-model="itemKeyword" placeholder="搜索武器或装备名称" clearable style="width: 240px" @keyup.enter="loadItems" />
          <el-button type="primary" icon="Plus" @click="openItemDialog()">新增装备数值</el-button>
        </div>
        <el-table :data="itemList" border stripe>
          <el-table-column prop="itemName" label="装备/武器名称" width="180" />
          <el-table-column prop="itemType" label="类型" width="130" />
          <el-table-column prop="damageInfo" label="面板/伤害数值" min-width="180" />
          <el-table-column prop="scalingOrFeature" label="特性/属性补正" min-width="180" />
          <el-table-column prop="obtainMethod" label="解锁/获取方式" min-width="180" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openItemDialog(row)">编辑</el-button>
              <el-button size="small" link type="danger" @click="delItem(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="收录游戏基础字典" name="games">
        <div style="display: flex; justify-content: flex-end; margin-bottom: 16px">
          <el-button type="primary" icon="Plus" @click="openGameDialog()">新增游戏分类</el-button>
        </div>
        <el-table :data="gameList" border stripe>
          <el-table-column prop="id" label="游戏ID" width="80" />
          <el-table-column prop="gameName" label="游戏名称" width="220" />
          <el-table-column prop="genre" label="游戏类型" width="200" />
          <el-table-column prop="platform" label="运行平台" width="150" />
          <el-table-column prop="description" label="游戏简介" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openGameDialog(row)">编辑</el-button>
              <el-button size="small" link type="danger" @click="delGame(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="itemVisible" title="维护装备数值（保存后 AI 助手可立即查到）" width="500px">
      <el-form :model="itemForm" label-width="95px">
        <el-form-item label="所属游戏ID"><el-input-number v-model="itemForm.gameId" :min="1" /></el-form-item>
        <el-form-item label="装备名称"><el-input v-model="itemForm.itemName" /></el-form-item>
        <el-form-item label="装备类型"><el-input v-model="itemForm.itemType" /></el-form-item>
        <el-form-item label="伤害/面板"><el-input v-model="itemForm.damageInfo" /></el-form-item>
        <el-form-item label="特性/补正"><el-input v-model="itemForm.scalingOrFeature" /></el-form-item>
        <el-form-item label="获取方式"><el-input v-model="itemForm.obtainMethod" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="gameVisible" title="维护游戏基础信息" width="480px">
      <el-form :model="gameForm" label-width="85px">
        <el-form-item label="游戏名称"><el-input v-model="gameForm.gameName" /></el-form-item>
        <el-form-item label="游戏类型"><el-input v-model="gameForm.genre" /></el-form-item>
        <el-form-item label="支持平台"><el-input v-model="gameForm.platform" /></el-form-item>
        <el-form-item label="简介说明"><el-input v-model="gameForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gameVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGame">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const activeTab = ref('items')
const itemKeyword = ref('')
const itemList = ref([])
const gameList = ref([])

const itemVisible = ref(false)
const itemForm = reactive({ id: null, gameId: 1, itemName: '', itemType: '', damageInfo: '', scalingOrFeature: '', obtainMethod: '' })

const gameVisible = ref(false)
const gameForm = reactive({ id: null, gameName: '', genre: '', platform: 'PC', description: '' })

const loadItems = async () => {
  const res = await request.get('/api/basic/items', { params: { keyword: itemKeyword.value } })
  itemList.value = res.data
}

const loadGames = async () => {
  const res = await request.get('/api/basic/games')
  gameList.value = res.data
}

const openItemDialog = (row) => {
  Object.assign(itemForm, row || { id: null, gameId: 1, itemName: '', itemType: '', damageInfo: '', scalingOrFeature: '', obtainMethod: '' })
  itemVisible.value = true
}

const saveItem = async () => {
  await request.post('/api/basic/items/save', itemForm)
  ElMessage.success('装备数值保存成功')
  itemVisible.value = false
  loadItems()
}

const delItem = async (id) => {
  await request.delete(`/api/basic/items/${id}`)
  ElMessage.success('已删除')
  loadItems()
}

const openGameDialog = (row) => {
  Object.assign(gameForm, row || { id: null, gameName: '', genre: '', platform: 'PC', description: '' })
  gameVisible.value = true
}

const saveGame = async () => {
  await request.post('/api/basic/games/save', gameForm)
  ElMessage.success('游戏字典保存成功')
  gameVisible.value = false
  loadGames()
}

const delGame = async (id) => {
  await request.delete(`/api/basic/games/${id}`)
  ElMessage.success('已删除')
  loadGames()
}

onMounted(() => {
  loadItems()
  loadGames()
})
</script>
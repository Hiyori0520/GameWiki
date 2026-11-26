<template>
  <div>
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="知识库收录攻略总数" :value="overview.totalGuides" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="待审核入库攻略" :value="overview.pendingGuides">
            <template #suffix><el-tag size="small" type="warning" style="margin-left: 6px">待处理</el-tag></template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="装备/武器数值条目" :value="overview.totalItems" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="系统注册玩家数" :value="overview.totalUsers" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span style="font-weight: 600">各游戏攻略收录分布（ECharts 实时统计）</span>
          </template>
          <div ref="chartRef" style="height: 340px"></div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600">Redis ZSet 实时攻略热度排行榜 Top 5</span>
              <el-tag type="danger" size="small">Redis 缓存实时统计</el-tag>
            </div>
          </template>
          <el-table :data="overview.hotRankList" stripe style="height: 340px">
            <el-table-column type="index" label="排名" width="70" align="center" />
            <el-table-column prop="guideCode" label="攻略编号" width="165" />
            <el-table-column prop="title" label="攻略标题" show-overflow-tooltip />
            <el-table-column prop="hotScore" label="热度分" width="90" align="center">
              <template #default="{ row }">
                <el-tag type="danger">{{ row.hotScore }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'

const chartRef = ref(null)
const overview = ref({
  totalGuides: 0,
  pendingGuides: 0,
  totalItems: 0,
  totalUsers: 0,
  gameDistribution: [],
  hotRankList: []
})

onMounted(async () => {
  const res = await request.get('/api/dashboard/overview')
  overview.value = res.data

  const chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
    legend: { bottom: 10, textStyle: { color: '#bac5de' } },
    series: [
      {
        name: '攻略数量',
        type: 'pie',
        radius: ['42%', '68%'],
        itemStyle: { borderRadius: 8, borderColor: '#182749', borderWidth: 2 },
        data: overview.value.gameDistribution
      }
    ]
  })
  window.addEventListener('resize', () => chart.resize())
})
</script>
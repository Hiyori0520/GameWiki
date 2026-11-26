<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue'
import { init, use } from 'echarts/core'
import type { ECOption } from '../../admin/chartOptions'
import type { EChartsType } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ option: ECOption; label: string }>()
const container = useTemplateRef<HTMLElement>('container')
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined

function applyOption(option: ECOption) {
  chart?.setOption({
    ...option,
    tooltip: {
      backgroundColor: '#202e51',
      borderColor: '#2a3e63',
      textStyle: { color: '#f5f6ff' },
      ...option.tooltip,
    },
  }, { notMerge: true })
}

onMounted(() => {
  if (!container.value) return
  chart = init(container.value)
  applyOption(props.option)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value)
})
watch(() => props.option, applyOption)
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="container" class="admin-chart" role="img" :aria-label="label"></div>
</template>

<style scoped>
.admin-chart { height: 197px; min-width: 0; }
</style>

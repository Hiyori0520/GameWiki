import type { ComposeOption } from 'echarts/core'
import type { LineSeriesOption, PieSeriesOption } from 'echarts/charts'
import type { GridComponentOption, LegendComponentOption, TooltipComponentOption } from 'echarts/components'

export type ECOption = ComposeOption<LineSeriesOption | PieSeriesOption | GridComponentOption | LegendComponentOption | TooltipComponentOption>

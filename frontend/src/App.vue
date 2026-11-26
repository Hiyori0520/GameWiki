<script setup>
import ConfigProvider from 'ant-design-vue/es/config-provider'
import theme from 'ant-design-vue/es/theme'

// Ant Design generates its component styles in JavaScript, including teleported menus.
const palette = getComputedStyle(document.documentElement)
const token = (name) => palette.getPropertyValue(`--gw-${name}`).trim()
const componentTheme = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: token('accent'),
    colorBgContainer: token('surface'),
    colorBgElevated: token('surface-raised'),
    colorBorder: token('line'),
    colorText: token('text'),
    colorTextSecondary: token('muted'),
    colorTextPlaceholder: token('subtle'),
  },
}
</script>

<template>
  <ConfigProvider :theme="componentTheme">
    <router-view />
  </ConfigProvider>
</template>

<template>
  <div class="mobile-page">
    <div class="mobile-hero">
      <h2>WMS 手机端</h2>
      <p>适合现场扫码、封存解封、转包、退库和出入库作业</p>
    </div>
    <div class="mobile-grid">
      <div class="mobile-card" @click="go('/mobile/inbound-scan')">
        <strong>扫码入库</strong>
        <span>扫码入库，并可进入入库单列表</span>
      </div>
      <div class="mobile-card" @click="go('/mobile/outbound-scan')">
        <strong>带单出库</strong>
        <span>扫描出库看板，并可进入出库单列表</span>
      </div>
      <div class="mobile-card" @click="go('/mobile/direct-outbound')">
        <strong>不带单出库</strong>
        <span>直接扫描库存看板出库</span>
      </div>
      <div class="mobile-card" @click="go('/mobile/kanban')">
        <strong>封存 / 解封</strong>
        <span>扫码查看库存看板并操作</span>
      </div>
      <div class="mobile-card" @click="go('/mobile/repack')">
        <strong>转包</strong>
        <span>手持转包，并可进入转包单列表</span>
      </div>
      <div class="mobile-card" @click="go('/mobile/return')">
        <strong>退库</strong>
        <span>将已出库看板退回库存</span>
      </div>
      <div class="mobile-card" @click="go('/home')">
        <strong>桌面端</strong>
        <span>返回完整管理后台</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.mobile-page { min-height: 100vh; background: #f5f7fb; padding: 18px; box-sizing: border-box; }
.mobile-hero { padding: 22px 18px; border-radius: 18px; color: #fff; background: linear-gradient(135deg, #304156, #409eff); }
.mobile-hero h2 { margin: 0 0 8px; }
.mobile-hero p { margin: 0; opacity: .88; line-height: 1.6; }
.mobile-grid { display: grid; grid-template-columns: 1fr; gap: 14px; margin-top: 18px; padding-bottom: 24px; }
.mobile-card { padding: 18px; border-radius: 16px; background: #fff; box-shadow: 0 8px 20px rgba(0,0,0,.06); }
.mobile-card strong { display: block; font-size: 18px; color: #303133; margin-bottom: 8px; }
.mobile-card span { color: #909399; font-size: 14px; line-height: 1.5; }
</style>

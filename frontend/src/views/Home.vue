<template>
  <div class="home-container">
    <div class="welcome">
      <h1>欢迎使用 WMS 仓储管理系统</h1>
      <p>请选择常用功能，或使用 AI 仓库管理员一句话完成查询和操作</p>
      <div class="quick-links">
        <el-row :gutter="16">
          <el-col :xs="12" :sm="8" :md="6" v-for="link in links" :key="link.path">
            <el-card shadow="hover" class="link-card" @click="$router.push(link.path)">
              <el-icon :size="32" :color="link.color"><component :is="link.icon" /></el-icon>
              <p>{{ link.title }}</p>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup>
const links = [
  { title: 'AI仓库管理员', path: '/ai/admin', icon: 'ChatDotRound', color: '#8E44AD' },
  { title: '入库单管理', path: '/inbound/order', icon: 'Tickets', color: '#409EFF' },
  { title: '看板管理', path: '/kanban/manage', icon: 'Grid', color: '#67C23A' },
  { title: '扫码入库', path: '/inbound/scan', icon: 'Camera', color: '#E6A23C' },
  { title: '库存总览', path: '/inventory/stock', icon: 'DataAnalysis', color: '#F56C6C' },
  { title: '手机端', path: '/mobile', icon: 'Cellphone', color: '#909399' }
]
</script>

<style scoped>
.home-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 120px);
}
.welcome { text-align: center; }
.welcome h1 { font-size: 28px; color: #303133; margin-bottom: 8px; }
.welcome p { color: #909399; margin-bottom: 40px; font-size: 16px; }
.quick-links { max-width: 900px; }
.link-card {
  cursor: pointer;
  text-align: center;
  padding: 20px 0;
  transition: transform 0.2s;
}
.link-card:hover { transform: translateY(-4px); }
.link-card p { margin-top: 10px; font-size: 14px; color: #606266; }
</style>

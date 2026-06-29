<template>
  <div class="layout">
    <el-container style="height: 100%">
      <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
        <div class="logo" @click="goHome">
          <span v-if="!isCollapse" class="logo-text">WMS管理系统</span>
          <span v-else class="logo-text-mini">WMS</span>
        </div>
        <el-menu
          class="side-menu"
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="false"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <template v-for="menu in menus" :key="menu.path">
            <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path">
              <template #title>
                <el-icon><component :is="menu.icon" /></el-icon>
                <span>{{ menu.title }}</span>
              </template>
              <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
                <el-icon><component :is="child.icon" /></el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.path">
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn" @click="isCollapse = !isCollapse" :size="20">
              <Fold v-if="!isCollapse" /><Expand v-else />
            </el-icon>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
              <el-breadcrumb-item v-if="currentTitle && currentTitle !== '首页'">{{ currentTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <div class="header-right">
            <el-dropdown @command="handleCommand">
              <span class="user-info">
                <el-icon><Avatar /></el-icon>
                <span>{{ userStore.username }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="home">首页</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <div class="tabs-bar">
          <el-tabs
            v-model="activeTab"
            type="card"
            closable
            @tab-click="handleTabClick"
            @tab-remove="handleTabRemove"
          >
            <el-tab-pane
              v-for="tab in tabs"
              :key="tab.path"
              :label="tab.title"
              :name="tab.path"
            />
          </el-tabs>
        </div>

        <el-main>
          <router-view v-slot="{ Component }">
            <KeepAlive>
              <component :is="Component" />
            </KeepAlive>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getMenus } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isCollapse = ref(false)
const menus = ref([])
const tabs = ref([])
const activeTab = ref('')

onMounted(async () => {
  try {
    const res = await getMenus()
    menus.value = res.data || []
  } catch { /* ignore */ }

  const cached = sessionStorage.getItem('tabs')
  if (cached) tabs.value = JSON.parse(cached)

  // 如果没有标签且当前不在首页，跳转首页
  if (!tabs.value.length && route.path !== '/home') {
    router.replace('/home')
  }
  activeTab.value = route.path
})

const activeMenu = computed(() => {
  const p = route.path
  if (p === '/home') return ''
  if (p.startsWith('/inbound/')) return p
  if (p.startsWith('/baseinfo/')) return p
  if (p.startsWith('/inventory/')) return p
  if (p.startsWith('/system/')) return p
  return p
})

const currentTitle = computed(() => route.meta.title || '')

function findMenuTitle(path) {
  for (const menu of menus.value) {
    if (menu.path === path) return menu.title
    if (menu.children) {
      for (const child of menu.children) {
        if (child.path === path) return child.title
      }
    }
  }
  return route.meta.title || path
}

// 点击logo回到首页
function goHome() {
  activeTab.value = ''
  router.push('/home')
}

function addTab(path, title) {
  if (path === '/home') return  // 首页不加到标签页
  if (!tabs.value.find(t => t.path === path)) {
    tabs.value.push({ path, title })
  }
  activeTab.value = path
  sessionStorage.setItem('tabs', JSON.stringify(tabs.value))
}

function handleTabClick(pane) {
  router.push(pane.paneName)
}

function handleTabRemove(name) {
  const idx = tabs.value.findIndex(t => t.path === name)
  if (idx === -1) return
  tabs.value.splice(idx, 1)
  sessionStorage.setItem('tabs', JSON.stringify(tabs.value))
  if (name === activeTab.value) {
    const next = tabs.value[idx] || tabs.value[idx - 1]
    if (next) {
      activeTab.value = next.path
      router.push(next.path)
    } else {
      // 所有标签页关闭 → 回到首页
      activeTab.value = ''
      router.push('/home')
    }
  }
}

watch(() => route.path, (path) => {
  if (path === '/home') {
    activeTab.value = ''
    return
  }
  activeTab.value = path
  const title = findMenuTitle(path)
  const exist = tabs.value.find(t => t.path === path)
  if (!exist) {
    tabs.value.push({ path, title })
  }
  sessionStorage.setItem('tabs', JSON.stringify(tabs.value))
}, { immediate: true })

function handleCommand(cmd) {
  if (cmd === 'home') {
    router.push('/home')
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      sessionStorage.removeItem('tabs')
      router.push('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside {
  background: #304156;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  flex: 0 0 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-text { color: #fff; font-size: 18px; font-weight: bold; }
.logo-text-mini { color: #fff; font-size: 14px; font-weight: bold; }
.side-menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
}
.side-menu:not(.el-menu--collapse) { width: 220px; }
.side-menu.el-menu--collapse { width: 64px; }
.side-menu::-webkit-scrollbar { width: 6px; }
.side-menu::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.22); border-radius: 4px; }
.side-menu::-webkit-scrollbar-track { background: transparent; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 16px;
  height: 50px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { cursor: pointer; }
.collapse-btn:hover { color: #409EFF; }
.header-right { display: flex; align-items: center; }
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #333;
  font-size: 14px;
}
.tabs-bar {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 12px;
}
.tabs-bar :deep(.el-tabs__header) { margin: 0; }
.tabs-bar :deep(.el-tabs__nav) { border: none; }
.el-main { background: #f0f2f5; padding: 16px; }
</style>

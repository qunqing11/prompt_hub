<template>
  <div class="app-container home">
    <el-row :gutter="20">
      <el-col :sm="24" :lg="24">
        <div class="welcome-container">
          <h1 class="title">欢迎使用 Prompt Hub 多租户管理系统</h1>
          <p class="subtitle">这是一个基于 SpringBoot + Vue + SaToken 架构的多租户 Prompt 管理与扣费系统。</p>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: 'Index',
  data() {
    return {}
  },
}
</script>

<style scoped lang="scss">
.home {
  display: flex;
  justify-content: center;
  align-items: center;
  height: calc(100vh - 84px);
  background-color: #f0f2f5;

  .welcome-container {
    text-align: center;
    background: #fff;
    padding: 60px 100px;
    border-radius: 10px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.1);

    .title {
      font-size: 32px;
      font-weight: bold;
      color: #333;
      margin-bottom: 20px;
    }

    .subtitle {
      font-size: 18px;
      color: #666;
    }
  }
}
</style>

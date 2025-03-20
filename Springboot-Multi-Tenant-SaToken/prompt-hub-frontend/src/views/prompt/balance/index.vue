<template>
  <div class="app-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span>租户资产管理</span>
      </div>
      <div class="text item" v-loading="loading">
        <el-row :gutter="20">
          <el-col :span="8">
            <div class="balance-card">
              <div class="label">当前租户余额 (积分)</div>
              <div class="value">{{ balanceInfo.balance }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="balance-card">
              <div class="label">更新时间</div>
              <div class="value-small">{{ parseTime(balanceInfo.updateTime) || '暂无更新' }}</div>
            </div>
          </el-col>
        </el-row>
        <div style="margin-top: 30px;">
          <el-button type="primary" icon="el-icon-refresh" @click="getTenantBalance">刷新余额</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getBalanceInfo } from "@/api/prompt/balance";

export default {
  name: "Balance",
  data() {
    return {
      loading: true,
      balanceInfo: {
        balance: '0.00',
        updateTime: null
      }
    };
  },
  created() {
    this.getTenantBalance();
    if (this.$eventBus) {
      this.$eventBus.$on("balance:refresh", this.getTenantBalance);
      this.$eventBus.$on("balance:updated", (nextBalanceInfo) => {
        if (nextBalanceInfo) {
          this.balanceInfo = nextBalanceInfo;
          this.loading = false;
        }
      });
    }
  },
  beforeDestroy() {
    if (this.$eventBus) {
      this.$eventBus.$off("balance:refresh", this.getTenantBalance);
      this.$eventBus.$off("balance:updated");
    }
  },
  methods: {
    getTenantBalance() {
      this.loading = true;
      getBalanceInfo().then(res => {
        if (res.data) {
          this.balanceInfo = res.data;
        }
        this.loading = false;
      }).catch(() => {
        this.loading = false;
      });
    }
  }
};
</script>

<style scoped lang="scss">
.balance-card {
  padding: 20px;
  background: #f4f4f5;
  border-radius: 8px;
  text-align: center;
  
  .label {
    font-size: 14px;
    color: #606266;
    margin-bottom: 10px;
  }
  
  .value {
    font-size: 32px;
    font-weight: bold;
    color: #409EFF;
  }
  
  .value-small {
    font-size: 20px;
    color: #303133;
    line-height: 40px;
  }
}
</style>

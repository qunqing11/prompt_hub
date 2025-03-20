<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="模板标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入模板标题"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="templateList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="模板ID" align="center" prop="id" />
      <el-table-column label="模板标题" align="center" prop="title" />
      <el-table-column label="扣除积分" align="center" prop="price" />
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">
            {{ scope.row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-play"
            @click="handleRun(scope.row)"
          >测试运行</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="模板标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入模板标题" />
        </el-form-item>
        <el-form-item label="模板内容" prop="content">
          <el-input v-model="form.content" type="textarea" placeholder="请输入模板内容" :rows="4" />
        </el-form-item>
        <el-form-item label="扣除积分" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" placeholder="请输入扣除积分" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="测试运行" :visible.sync="runFormDialog.visible" width="560px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="补充说明">
          <el-input
            v-model="runFormDialog.inputText"
            type="textarea"
            :rows="4"
            placeholder="可选：补充你的要求，例如语气、受众、长度。"
          />
        </el-form-item>
        <el-form-item
          v-for="key in runFormDialog.varKeys"
          :key="key"
          :label="key"
        >
          <el-input v-model="runFormDialog.varValues[key]" :placeholder="'请输入' + key" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="runFormDialog.visible = false" :disabled="runFormDialog.running">取 消</el-button>
        <el-button type="primary" @click="submitRun" :loading="runFormDialog.running">运 行</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="AI 回复"
      :visible.sync="runResultDialog.visible"
      width="620px"
      append-to-body
    >
      <div style="border: 1px solid #ebeef5; border-radius: 10px; padding: 14px 16px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.06); min-height: 180px;">
        <pre style="white-space: pre-wrap; margin: 0; font-size: 14px; line-height: 22px; color: #303133;">{{ runResultDialog.displayedText || (runResultDialog.finished ? "（空）" : "思考中...") }}</pre>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="runResultDialog.visible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTemplate, getTemplate, delTemplate, addTemplate, updateTemplate, runTemplate, runTemplateStream } from "@/api/prompt/template";
import { getBalanceInfo } from "@/api/prompt/balance";

export default {
  name: "Template",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 表格数据
      templateList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      runFormDialog: {
        visible: false,
        running: false,
        row: null,
        inputText: "",
        varKeys: [],
        varValues: {}
      },
      runResultDialog: {
        visible: false,
        displayedText: "",
        finished: false
      },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        title: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        title: [
          { required: true, message: "模板标题不能为空", trigger: "blur" }
        ],
        content: [
          { required: true, message: "模板内容不能为空", trigger: "blur" }
        ],
        price: [
          { required: true, message: "扣除积分不能为空", trigger: "blur" }
        ]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询列表 */
    getList() {
      this.loading = true;
      listTemplate(this.queryParams).then(response => {
        this.templateList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        title: null,
        content: null,
        price: 0.00,
        status: "0",
        remark: null
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加提示词模板";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getTemplate(id).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改提示词模板";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateTemplate(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addTemplate(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除模板编号为"' + ids + '"的数据项？').then(function() {
        return delTemplate(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 测试运行按钮操作 */
    async handleRun(row) {
      try {
        await this.prepareRunDialog(row);
        this.runFormDialog.visible = true;
      } catch (e) {}
    },
    async prepareRunDialog(row) {
      this.runFormDialog.row = row;
      const detail = await getTemplate(row.id);
      const content = detail && detail.data && detail.data.content ? detail.data.content : (row.content || "");
      const varKeys = this.extractTemplateVars(content);
      const varValues = {};
      varKeys.forEach(key => {
        varValues[key] = this.runFormDialog.varValues[key] || "";
      });
      this.runFormDialog.varKeys = varKeys;
      this.runFormDialog.varValues = varValues;
      this.runFormDialog.inputText = "";
    },
    extractTemplateVars(content) {
      if (!content) {
        return [];
      }
      const pattern = /\{\{\s*([^{}]+?)\s*\}\}/g;
      const result = [];
      const exists = {};
      let match = pattern.exec(content);
      while (match) {
        const key = (match[1] || "").trim();
        if (key && !exists[key]) {
          exists[key] = true;
          result.push(key);
        }
        match = pattern.exec(content);
      }
      return result;
    },
    buildRunVars() {
      const vars = {};
      if (this.runFormDialog.inputText && this.runFormDialog.inputText.trim()) {
        vars.input = this.runFormDialog.inputText.trim();
      }
      this.runFormDialog.varKeys.forEach(key => {
        vars[key] = (this.runFormDialog.varValues[key] || "").trim();
      });
      return vars;
    },
    async submitRun() {
      if (!this.runFormDialog.row || this.runFormDialog.running) {
        return;
      }
      const missingKey = this.runFormDialog.varKeys.find(key => !(this.runFormDialog.varValues[key] || "").trim());
      if (missingKey) {
        this.$message.warning(`请先填写变量：${missingKey}`);
        return;
      }
      this.runFormDialog.running = true;
      this.runFormDialog.visible = false;
      this.runResultDialog.visible = true;
      this.runResultDialog.displayedText = "";
      this.runResultDialog.finished = false;
      const requestData = {
        templateId: this.runFormDialog.row.id,
        inputText: this.runFormDialog.inputText,
        vars: this.buildRunVars()
      };
      let donePayload = null;
      try {
        if (window.fetch && window.ReadableStream) {
          await runTemplateStream(requestData, {
            onChunk: (payload) => {
              this.appendStreamChunk(payload);
            },
            onDone: (payload) => {
              donePayload = payload || {};
              const resultText = this.normalizeRunResultText((donePayload.result || donePayload.reply || this.runResultDialog.displayedText || "").trim());
              this.runResultDialog.displayedText = resultText;
              this.runResultDialog.finished = true;
            },
            onError: (payload) => {
              throw new Error(payload && payload.message ? payload.message : "流式运行失败");
            }
          });
        } else {
          const res = await runTemplate(requestData);
          donePayload = res && res.data ? res.data : {};
          const resultText = this.normalizeRunResultText(donePayload.result || donePayload.reply || "");
          this.runResultDialog.displayedText = resultText;
          this.runResultDialog.finished = true;
        }
        if (!this.runResultDialog.finished) {
          this.runResultDialog.finished = true;
        }
        await this.refreshBalance(donePayload ? donePayload.balanceInfo : null);
      } catch (e) {
        this.runResultDialog.finished = true;
        this.$message.error(e && e.message ? e.message : "运行失败");
      } finally {
        this.runFormDialog.running = false;
      }
    },
    appendStreamChunk(payload) {
      let text = "";
      if (typeof payload === "string") {
        text = payload;
      } else if (payload && payload.text) {
        text = payload.text;
      }
      if (!text) {
        return;
      }
      this.runResultDialog.displayedText += text;
    },
    normalizeRunResultText(text) {
      if (!text) {
        return "";
      }
      return String(text)
        .replace(/^```(?:json|text|markdown)?\s*/i, "")
        .replace(/\s*```$/, "")
        .trim();
    },
    async refreshBalance(balanceInfo) {
      if (balanceInfo) {
        if (this.$eventBus) {
          this.$eventBus.$emit("balance:updated", balanceInfo);
        }
        return;
      }
      try {
        const balanceRes = await getBalanceInfo();
        if (balanceRes && balanceRes.data) {
          if (this.$eventBus) {
            this.$eventBus.$emit("balance:updated", balanceRes.data);
          }
        } else if (this.$eventBus) {
          this.$eventBus.$emit("balance:refresh");
        }
      } catch (e) {
        if (this.$eventBus) {
          this.$eventBus.$emit("balance:refresh");
        }
      }
    }
  }
};
</script>

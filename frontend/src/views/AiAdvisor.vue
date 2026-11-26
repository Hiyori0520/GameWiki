<template>
  <el-card shadow="never" style="height: calc(100vh - 105px); display: flex; flex-direction: column">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div>
          <span style="font-weight: 600; font-size: 16px">AI 游戏战术顾问智能体</span>
          <el-tag type="primary" size="small" style="margin-left: 10px">Spring AI Function Calling 自主查库</el-tag>
          <el-tag v-if="sessionId" type="info" size="small" style="margin-left: 8px">会话记忆 ID: {{ sessionId }}</el-tag>
        </div>
        <el-button size="small" icon="Refresh" @click="resetSession">开启新对话</el-button>
      </div>
    </template>

    <div style="margin-bottom: 12px; display: flex; gap: 8px; flex-wrap: wrap">
      <span style="font-size: 13px; color: var(--gw-muted); line-height: 24px">演示快捷提问：</span>
      <el-button size="small" round @click="quickAsk('帮我查一下本地知识库里关于攻击机桶滚或High Yo-Yo的攻略')">
        战地1攻击机桶滚攻略
      </el-button>
      <el-button size="small" round @click="quickAsk('做这个机动动作时，什么时候切换后座机枪反杀？')">
        追问：后座反杀时机（测多轮记忆）
      </el-button>
      <el-button size="small" round @click="quickAsk('查询《黑暗之魂3》中亚斯特拉大剑的伤害面板和获取方式')">
        查装备面板数值
      </el-button>
    </div>

    <div ref="chatBoxRef" class="chat-history">
      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg-row', msg.role === 'user' ? 'msg-user' : 'msg-ai']">
        <div class="msg-bubble">
          <div style="font-size: 12px; opacity: 0.75; margin-bottom: 4px">
            {{ msg.role === 'user' ? '指挥官（我）' : 'AI 战术顾问' }}
          </div>
          <div style="white-space: pre-wrap; line-height: 1.65">{{ msg.content }}</div>
        </div>
      </div>
      <div v-if="thinking" class="msg-row msg-ai">
        <div class="msg-bubble" style="color: var(--gw-muted)">
          正在检索本地知识库与武器数值面板，请稍候...
        </div>
      </div>
    </div>

    <div style="display: flex; gap: 12px; margin-top: 14px">
      <el-input
        v-model="inputQuestion"
        placeholder="输入任何游戏战术、BOSS机制或装备面板问题，按 Enter 发送..."
        size="large"
        @keyup.enter="sendQuestion"
      />
      <el-button type="primary" size="large" :loading="thinking" @click="sendQuestion">发送提问</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import request from '../utils/request'

const sessionId = ref('')
const inputQuestion = ref('')
const thinking = ref(false)
const chatBoxRef = ref(null)

const messages = ref([
  {
    role: 'ai',
    content: '你好！我是【AI 游戏战术顾问】。我使用同一个 MySQL 知识库查询攻略和装备数值，并保存多轮对话记录。你可以直接向我询问载具机动、BOSS打法或装备面板！'
  }
])

const scrollToBottom = async () => {
  await nextTick()
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  }
}

const quickAsk = (text) => {
  inputQuestion.value = text
  sendQuestion()
}

const sendQuestion = async () => {
  const q = inputQuestion.value.trim()
  if (!q || thinking.value) return

  messages.value.push({ role: 'user', content: q })
  inputQuestion.value = ''
  thinking.value = true
  scrollToBottom()

  try {
    const res = await request.post('/api/ai/chat', {
      sessionId: sessionId.value,
      question: q
    })
    sessionId.value = res.data.sessionId
    messages.value.push({ role: 'ai', content: res.data.reply })
  } finally {
    thinking.value = false
    scrollToBottom()
  }
}

const resetSession = () => {
  sessionId.value = ''
  messages.value = [
    { role: 'ai', content: '已开启全新战术会话！请问有什么可以帮您？' }
  ]
}
</script>

<style scoped>
.chat-history {
  height: calc(100vh - 285px);
  overflow-y: auto;
  padding: 16px;
  background: var(--gw-surface);
  border-radius: 8px;
  border: 1px solid var(--gw-line);
}
.msg-row {
  display: flex;
  margin-bottom: 14px;
}
.msg-user {
  justify-content: flex-end;
}
.msg-ai {
  justify-content: flex-start;
}
.msg-bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 14px;
}
.msg-user .msg-bubble {
  background: var(--gw-primary);
  color: #fff;
}
.msg-ai .msg-bubble {
  background: var(--gw-surface-raised);
  color: var(--gw-text);
  border: 1px solid var(--gw-line);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
</style>
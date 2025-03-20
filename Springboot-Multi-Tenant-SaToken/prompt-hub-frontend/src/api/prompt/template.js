import request from '@/utils/request'
import { getToken } from '@/utils/auth'

export function listTemplate(query) {
  return request({
    url: '/selfcom/promptTemplate/list',
    method: 'get',
    params: query
  })
}

export function getTemplate(id) {
  return request({
    url: '/selfcom/promptTemplate/' + id,
    method: 'get'
  })
}

export function addTemplate(data) {
  return request({
    url: '/selfcom/promptTemplate',
    method: 'post',
    data: data
  })
}

export function updateTemplate(data) {
  return request({
    url: '/selfcom/promptTemplate',
    method: 'put',
    data: data
  })
}

export function delTemplate(id) {
  return request({
    url: '/selfcom/promptTemplate/' + id,
    method: 'delete'
  })
}

export function runTemplate(data) {
  return request({
    url: '/prompt/run',
    method: 'post',
    data: data,
    timeout: 120000
  })
}

export async function runTemplateStream(data, handlers = {}) {
  const token = getToken()
  const response = await fetch(`${process.env.VUE_APP_BASE_API}/prompt/run/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })
  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败：HTTP ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = consumeSseBuffer(buffer, handlers)
  }
  if (buffer) {
    consumeSseBlock(buffer, handlers)
  }
}

function consumeSseBuffer(buffer, handlers) {
  const normalized = buffer.replace(/\r\n/g, '\n')
  let working = normalized
  let splitIndex = working.indexOf('\n\n')
  while (splitIndex !== -1) {
    const block = working.slice(0, splitIndex)
    consumeSseBlock(block, handlers)
    working = working.slice(splitIndex + 2)
    splitIndex = working.indexOf('\n\n')
  }
  return working
}

function consumeSseBlock(block, handlers) {
  if (!block || !block.trim()) {
    return
  }
  const lines = block.split('\n')
  let eventName = 'message'
  const dataLines = []
  lines.forEach(line => {
    if (line.startsWith('event:')) {
      eventName = line.substring(6).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.substring(5).trim())
    }
  })
  const rawData = dataLines.join('\n')
  let payload = rawData
  try {
    payload = rawData ? JSON.parse(rawData) : {}
  } catch (e) {}
  if (eventName === 'chunk' && handlers.onChunk) {
    handlers.onChunk(payload)
    return
  }
  if (eventName === 'done' && handlers.onDone) {
    handlers.onDone(payload)
    return
  }
  if (eventName === 'error') {
    if (handlers.onError) {
      handlers.onError(payload)
      return
    }
    throw new Error(payload && payload.message ? payload.message : '流式运行失败')
  }
}

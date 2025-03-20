import request from '@/utils/request'

export function getBalanceInfo() {
  return request({
    url: '/selfcom/tenantBalance/info',
    method: 'get'
  })
}

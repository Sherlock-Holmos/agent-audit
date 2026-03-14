import client from './client'

export const chatWithAssistant = (question) =>
  client.post('/agent/chat', { question })

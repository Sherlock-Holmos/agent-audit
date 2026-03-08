# Agent 服务接口文档

## 服务职责
- 审计领域智能问答
- 综合数据服务返回分析建议

## 基础信息
- 服务地址：`http://localhost:8083`
- 网关访问：`http://localhost:8081/api/agent/**`

## 已实现接口

### 1) 智能问答
- `POST /api/agent/chat`
- 请求体：
```json
{
  "question": "本周整改薄弱环节是什么？"
}
```
- 响应：
```json
{
  "question": "本周整改薄弱环节是什么？",
  "answer": "AI建议：...",
  "confidence": 0.91
}
```

## 规划接口（待实现）
- `POST /api/agent/chat/stream`：流式回答（SSE）
- `POST /api/agent/report/generate`：生成审计整改报告
- `GET /api/agent/report/{reportId}`：查询报告详情
- `POST /api/agent/insight/risk-detect`：自动识别高风险整改项
- `POST /api/agent/knowledge/reindex`：知识库增量更新

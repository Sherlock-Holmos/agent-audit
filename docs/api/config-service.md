# Config 服务接口文档

## 1. 服务职责
- 系统阈值配置管理
- 配置读取能力对外提供

## 2. 基础信息
- 服务地址：`http://localhost:8084`
- 网关访问：`http://localhost:8081/api/config/**`

## 3. 已实现接口
1. `GET /api/config/threshold`

示例响应：
```json
{
  "overdueDays": 7,
  "minRate": 85,
  "warningChannel": "system"
}
```

## 4. 规划接口
- `GET /api/config/settings/tree`
- `GET /api/config/settings/{groupKey}`
- `PUT /api/config/settings/{groupKey}`
- `POST /api/config/settings/validate`
- `GET /api/config/history`
- `POST /api/config/history/{id}/rollback`

# Config 服务接口文档

## 服务职责
- 系统阈值与配置管理
- 为前端“设置”分级页面提供配置接口

## 基础信息
- 服务地址：`http://localhost:8084`
- 网关访问：`http://localhost:8081/api/config/**`

## 已实现接口

### 1) 获取阈值配置
- `GET /api/config/threshold`
- 响应：
```json
{
  "overdueDays": 7,
  "minRate": 85,
  "warningChannel": "system"
}
```

## 规划接口（待实现）
- `GET /api/config/settings/tree`：获取分级设置树
- `GET /api/config/settings/{groupKey}`：获取设置分组详情
- `PUT /api/config/settings/{groupKey}`：保存设置分组
- `POST /api/config/settings/validate`：设置项校验（如阈值、端口范围）
- `GET /api/config/history`：配置变更历史
- `POST /api/config/history/{id}/rollback`：配置回滚

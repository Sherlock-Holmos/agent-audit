# API 总览

## 1. 统一约定
- 网关入口：`http://localhost:8081`
- 统一前缀：`/api/**`
- 认证：`Authorization: Bearer <token>`
- 幂等：异步执行接口支持 `Idempotency-Key`

## 2. 服务文档索引
- 网关服务：[api/gateway.md](api/gateway.md)
- 认证服务：[api/auth-service.md](api/auth-service.md)
- 数据服务：[api/data-service.md](api/data-service.md)
- 智能体服务：[api/agent-service.md](api/agent-service.md)
- 配置服务：[api/config-service.md](api/config-service.md)

## 3. 推荐联调顺序
1. 登录获取 token（auth-service）
2. 调用 data-service 查询驾驶舱与任务列表
3. 调用清洗/融合同步或异步执行接口
4. 调用 agent-service 获取问答结果

## 4. 错误码约定（建议）
- `0`：成功
- `400`：参数或业务校验错误
- `401`：未认证/认证失败
- `429`：触发限流
- `500`：系统内部错误

## 5. 变更提示
- 最新版本新增 data-service 异步任务接口与任务状态查询。
- 网关新增用户/IP 双维度限流能力。

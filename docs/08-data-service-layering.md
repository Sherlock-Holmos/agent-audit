# Data-Service 分层与目录规范

## 目的

Data-Service 已完成从单体胖服务向职责拆分的演进。本规范用于约束新增代码的落位方式，避免职责回流到单一类中。

## 分层原则

1. 编排层只负责编排流程，不承载复杂规则细节。
2. 领域层承载业务规则与校验。
3. 基础设施层承载文件、表、外部系统等技术细节。
4. 缓存层集中管理缓存策略。
5. 服务接口层仅定义能力契约。

## 当前目录结构

Data-Service 服务层目录按职责归位如下：

- `services/data-service/src/main/java/com/audit/data/service/orchestration`
  - `DataProcessService`
  - `DataProcessAsyncService`
- `services/data-service/src/main/java/com/audit/data/service/domain`
  - `CleanConfigService`
  - `CleanRuleEngineService`
  - `WorkflowDefinitionService`
  - `GovernanceAuditService`
- `services/data-service/src/main/java/com/audit/data/service/infrastructure`
  - `StagingTableService`
  - `FileRowReader`
  - `DataProcessSchemaInitializer`
- `services/data-service/src/main/java/com/audit/data/service/cache`
  - `RedisCacheService`
  - `IRedisCacheService`
- `services/data-service/src/main/java/com/audit/data/service/api`
  - `IDataProcessService`
  - `IDataProcessAsyncService`

## 新增代码落位规则

1. 新增“规则解析、业务校验、策略执行”类：放 `service/domain`。
2. 新增“表管理、文件读写、外部调用适配”类：放 `service/infrastructure`。
3. 新增“流程编排、事务边界控制”类：放 `service/orchestration`。
4. 新增“缓存客户端、键策略、TTL 策略”类：放 `service/cache`。
5. 新增“可被上层依赖的能力契约”接口：放 `service/api`。

## 编码约束

1. 编排层可以依赖 domain/infrastructure/cache/api，但 domain 不应反向依赖 orchestration。
2. 控制器与 application 层优先依赖接口（`service/api`）。
3. 禁止把 SQL 拼装、文件解析、规则解释再次塞回编排类。
4. 单个类超过 800 行时必须评估拆分；超过 1200 行默认不允许继续增长。

## 迁移兼容说明

本次仅做文件目录归位，Java `package` 名保持 `com.audit.data.service`，以避免大规模 import 和 Bean 扫描风险。

后续若要统一为子包 package（例如 `com.audit.data.service.domain`），需单独提交并进行全量回归。

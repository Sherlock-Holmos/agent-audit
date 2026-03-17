# 代码架构与设计模式

## 概述

整个微服务系统均遵循 **SOLID 原则**，特别是依赖倒置原则（DIP），采用接口-实现分离的设计模式。

## 1. 整体设计理念

### 核心目标
- ✅ **低耦合**：服务间通过接口通信，隔离实现细节
- ✅ **高内聚**：单一职责原则，每个接口一个专注的用途
- ✅ **易测试**：接口支持 Mock 实现，便于单元测试和集成测试
- ✅ **易扩展**：新增实现无需修改现有代码，符合开闭原则（OCP）

## 2. Java 微服务接口体系

### 2.1 Auth-Service（认证服务）

#### IAuthUserService
```java
public interface IAuthUserService {
    Map<String, Object> register(String username, String password);
    Map<String, Object> authenticate(String username, String password);
    Map<String, Object> getProfileByUsername(String username);
    Map<String, Object> updateProfile(String username, Map<String, Object> payload);
    void deactivate(String username);
}
```

**实现**：`AuthUserServiceImpl`

**职责**：用户存储、密码加密、登录验证、资料管理

---

#### IJwtService
```java
public interface IJwtService {
    String generateToken(String username);
    String parseUsername(String token);
}
```

**实现**：`JwtServiceImpl`

**职责**：JWT 令牌的生成与解析

---

### 2.2 Config-Service（配置服务）

#### IConfigService
```java
public interface IConfigService {
    Map<String, Object> getThresholdConfig();
}
```

**实现**：`ConfigServiceImpl`

**职责**：阈值配置的读取与管理

---

### 2.3 Data-Service（数据服务）

最复杂的服务，包含 5 个核心接口：

#### IDashboardService
```java
public interface IDashboardService {
    List<Map<String, Object>> listFusionOptions(String ownerUsername);
    Map<String, Object> buildDashboard(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> buildTrend(...);
    Map<String, Object> buildHeatmap(...);
    void invalidateOwnerCache(String ownerUsername);
}
```

**实现**：`DashboardService`

**职责**：汇总数据源数据，构建驾驶舱视图，缓存管理

---

#### IDataProcessService
```java
public interface IDataProcessService {
    // 清洗规则管理
    List<Map<String, Object>> listCleanRules(String ownerUsername);
    Map<String, Object> uploadCleanRule(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> toggleCleanRule(String ownerUsername, Long id, boolean enabled);
    
    // 清洗策略管理
    List<Map<String, Object>> listCleanStrategies(String ownerUsername);
    Map<String, Object> createCleanStrategy(String ownerUsername, Map<String, Object> payload);
    
    // 清洗和融合任务
    List<Map<String, Object>> listCleanTasks(...);
    Map<String, Object> runCleanTask(String ownerUsername, Long id);
    List<Map<String, Object>> listFusionTasks(...);
    Map<String, Object> runFusionTask(String ownerUsername, Long id);
    // ... 共约 13 个方法
}
```

**实现**：`DataProcessService`

**职责**：数据清洗规则、标准化策略、模糊匹配、融合任务管理

---

#### IDataSourceService
```java
public interface IDataSourceService {
    // 数据源 CRUD
    List<Map<String, Object>> list(String ownerUsername, String keyword, String type, String status);
    void delete(String ownerUsername, Long id);
    Map<String, Object> updateStatus(String ownerUsername, Long id, String status);
    
    // 数据库和文件数据源
    Map<String, Object> createDatabase(String ownerUsername, Map<String, Object> payload);
    Map<String, Object> createFile(String ownerUsername, String name, String remark, MultipartFile file);
    
    // 数据源对象查询
    List<Map<String, Object>> listSourceObjects(String ownerUsername, Long id);
}
```

**实现**：`DataSourceService`

**职责**：数据源连接、元数据探查、文件上传、对象列表

---

#### IDataProcessAsyncService
```java
public interface IDataProcessAsyncService {
    Map<String, Object> startCleanTask(String ownerUsername, Long taskId, Map<String, Object> payload);
    Map<String, Object> startFusionTask(String ownerUsername, Long taskId, Map<String, Object> payload);
    Map<String, Object> getJobStatus(String jobId);
}
```

**实现**：`DataProcessAsyncService`

**职责**：异步任务调度、状态查询

---

#### IRedisCacheService
```java
public interface IRedisCacheService {
    <T> T getOrCompute(String key, Callable<T> loader, long ttlSeconds);
    void evictByPrefix(String prefix);
}
```

**实现**：`RedisCacheService`

**职责**：Redis 缓存策略、TTL 管理

---

### 2.4 Gateway（API 网关）

#### ITokenProvider
```java
public interface ITokenProvider {
    String validateAndExtractUsername(String token);
    String extractRole(String token);
}
```

**实现**：`JwtTokenProvider`

**职责**：JWT 验证与用户信息提取，独立于业务逻辑

---

#### IRateLimitProvider
```java
public interface IRateLimitProvider {
    boolean isExceeded(String userId);
    boolean isIpExceeded(String ip);
    int getUserCount(String userId);
    int getIpCount(String ip);
}
```

**实现**：`SlidingWindowRateLimitProvider`

**职责**：限流检查与速率计算，支持用户和 IP 维度

---

## 3. Python 微服务接口体系

### Agent-Service 采用 Python ABC（Abstract Base Class）定义接口

#### ILLMProvider
```python
class ILLMProvider(ABC):
    @abstractmethod
    def generate(self, prompt: str, history: List[str]) -> str:
        """生成 LLM 输出"""
        pass
```

**实现**：内置实现支持 OpenAI、Azure OpenAI

**职责**：LLM API 调用的抽象，支持多厂商切换

---

#### IRAGRetriever
```python
class IRAGRetriever(ABC):
    @abstractmethod
    def retrieve(self, question: str, k: int = 3) -> List[str]:
        """执行向量检索"""
        pass
```

**实现**：内置实现，预留向量库扩展位

**职责**：检索增强生成（RAG）的向量检索接口

---

#### IAgentService
```python
class IAgentService(ABC):
    @abstractmethod
    def run_agent(self, question: str, history: List[str], dashboard: dict) -> str:
        """执行智能问答"""
        pass
```

**实现**：`AgentServiceImpl`

**职责**：LLM + RAG 链的编排、上下文管理

---

#### ISessionManager
```python
class ISessionManager(ABC):
    @abstractmethod
    def try_acquire_quota(self, username: str) -> bool:
        """获取配额"""
        pass
    
    @abstractmethod
    def get_recent_history(self, username: str) -> List[Dict]:
        """获取会话历史"""
        pass
```

**实现**：`SessionService`

**职责**：会话上下文、配额限制、历史管理

---

#### IDashboardClient
```python
class IDashboardClient(ABC):
    @abstractmethod
    def fetch_dashboard(self, username: str) -> Dict:
        """获取驾驶舱数据"""
        pass
```

**实现**：`DashboardClient`

**职责**：驾驶舱数据的 HTTP 客户端调用

---

## 4. 依赖注入与服务链路

### 4.1 Java 服务间依赖链

```
AuthController
├─ IAuthUserService (interface)
│  └─ AuthUserServiceImpl (implementation)
└─ IJwtService (interface)
   └─ JwtServiceImpl (implementation)

DataProcessController
├─ IDataProcessService (interface)
│  ├─ DataProcessService (implementation)
│  ├─ 依赖 IDataSourceService (interface)
│  │  └─ DataSourceService (implementation)
│  └─ 依赖 IDashboardService (interface)
│     └─ DashboardService (implementation)
└─ IDataProcessAsyncService (interface)
   └─ DataProcessAsyncService (implementation)

Gateway JwtAuthFilter
├─ ITokenProvider (interface)
│  └─ JwtTokenProvider (implementation)
└─ IRateLimitProvider (interface)
   └─ SlidingWindowRateLimitProvider (implementation)
```

### 4.2 Python 服务依赖链

```
chat_router
└─ IAgentService (interface)
   └─ AgentServiceImpl (implementation)
      ├─ 使用 ILLMProvider
      ├─ 使用 IRAGRetriever
      ├─ 使用 ISessionManager
      └─ 使用 IDashboardClient
```

## 5. 设计模式应用

### 5.1 Strategy Pattern（策略模式）

**场景**：LLM Provider

```python
# 接口定义
class ILLMProvider(ABC):
    @abstractmethod
    def generate(self, prompt, history) -> str:
        pass

# 多个策略实现
class OpenAIProvider(ILLMProvider):
    def generate(self, prompt, history):
        # OpenAI API
        
class AzureOpenAIProvider(ILLMProvider):
    def generate(self, prompt, history):
        # Azure OpenAI API
```

**优势**：在 `AgentServiceImpl._build_llm()` 中根据配置动态选择 LLM 实现，无需修改核心逻辑

---

**场景**：RateLimitProvider

```java
interface IRateLimitProvider {
    boolean isExceeded(String userId);
}

// 可轻松扩展到其他限流策略
class SlidingWindowRateLimitProvider implements IRateLimitProvider { ... }
class TokenBucketProvider implements IRateLimitProvider { ... }
```

### 5.2 Factory Pattern（工厂模式）

```python
# agent_impl.py
class AgentServiceImpl(IAgentService):
    def _build_llm(self) -> ILLMProvider:
        """工厂方法：根据环境变量选择 LLM 提供商"""
        provider = os.getenv("LLM_PROVIDER", "openai")
        if provider == "azure":
            return AzureOpenAIProvider()
        else:
            return OpenAIProvider()
    
    def _build_retriever(self) -> IRAGRetriever:
        """工厂方法：根据配置选择检索实现"""
        # 当前返回默认实现，预留扩展空间
        return DefaultRetriever()
```

### 5.3 Adapter Pattern（适配器模式）

**场景**：TokenProvider 与 RateLimitProvider

```
JwtAuthFilter (业务逻辑)
    ↓ 委托
ITokenProvider (接口)
    ↓ 实现
JwtTokenProvider (适配器)
    ↓ 调用
JWT Library (第三方库)
```

**优势**：业务逻辑与第三方库解耦，更换 JWT 库只需修改适配器

---

## 6. 测试支持

### 6.1 Mock 实现示例

```java
// 单元测试中
class DataProcessServiceTest {
    private IDataSourceService mockDataSourceService;
    private IDashboardService mockDashboardService;
    
    @Before
    public void setup() {
        mockDataSourceService = mock(IDataSourceService.class);
        mockDashboardService = mock(IDashboardService.class);
        
        // 注入 mock
        service = new DataProcessService(
            jdbcTemplate, objectMapper, 
            mockDataSourceService, mockDashboardService
        );
    }
}
```

**无需修改生产代码，只需准备 Mock 实现即可**

---

### 6.2 Python Mock 示例

```python
class MockLLMProvider(ILLMProvider):
    def generate(self, prompt, history):
        return "Mock response"

# 测试时替换
agent = AgentServiceImpl()
agent.llm_provider = MockLLMProvider()
```

---

## 7. 架构收益总结

| 方面 | 收益 |
|------|------|
| **可维护性** | 接口作为契约，实现修改不影响调用方 |
| **可测试性** | 支持 Mock 实现，单元测试不依赖实际服务 |
| **可扩展性** | 新增实现无需修改现有代码（OCP 原则） |
| **可替换性** | 支持 Strategy 模式实现多个实现类 |
| **解耦程度** | 服务仅依赖接口，不依赖具体实现 |
| **职责清晰** | 接口清单使架构意图显式表达 |

---

## 8. 编译验证状态

✅ **所有服务编译通过**

```bash
# Java 服务
auth-service    : mvn clean compile -q → BUILD SUCCESS
config-service  : mvn clean compile -q → BUILD SUCCESS
data-service    : mvn clean compile -q → BUILD SUCCESS
gateway         : mvn clean compile -q → BUILD SUCCESS

# Python 服务
agent-service   : python -m py_compile *.py → OK
```

---

## 9. 后续优化方向

1. **接口文档生成**：使用 Javadoc + Sphinx 自动生成接口签名文档
2. **Mock 库完善**：补充完整的 Mock 实现用于集成测试
3. **性能优化**：在接口层引入缓存战略（如 `@Cacheable`）
4. **灰度升级**：基于接口-实现分离支持无缝升级新版本实现
5. **多租户隔离**：接口层支持租户上下文传递

---

## 10. 参考链接

- [02-architecture.md](./02-architecture.md) - 系统整体架构
- [API.md](./API.md) - API 端点文档
- [03-deployment.md](./03-deployment.md) - 部署指南

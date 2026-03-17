# Agent Audit

审计整改智能驾驶舱与多源数据融合平台，提供认证、数据处理、智能问答、配置管理与统一网关治理能力。当前系统为混合后端栈：核心业务服务使用 Spring Boot，agent-service 使用 Python、FastAPI 与 LangChain。

## 📚 文档入口

### 快速导航
- **企业级文档中心**：[docs/README.md](docs/README.md)
- **项目总览**：[docs/01-overview.md](docs/01-overview.md)
- **系统架构**：[docs/02-architecture.md](docs/02-architecture.md)
- **⭐ 代码架构与设计模式**：[docs/07-code-architecture.md](docs/07-code-architecture.md) - 接口化编程+SOLID原则
- **部署手册**：[docs/03-deployment.md](docs/03-deployment.md)
- **运维手册**：[docs/04-operations.md](docs/04-operations.md)
- **安全基线**：[docs/05-security-compliance.md](docs/05-security-compliance.md)
- **API 总览**：[docs/API.md](docs/API.md)
- **发布记录**：[docs/06-release-notes.md](docs/06-release-notes.md)

## 🚀 快速启动

### 1) 启动后端与基础设施

```bash
docker compose up -d mysql redis auth-service data-service config-service agent-service gateway
```

### 2) 启动前端开发

```bash
cd frontend
npm install
npm run dev
```

### 3) 启动完整环境（含监控）

```bash
docker compose up -d
```

## 🌐 主要访问地址

| 服务 | 地址 | 说明 |
|-----|------|------|
| 前端开发 | http://localhost:5173 | Vite 热更新服务 |
| API 网关 | http://localhost:8081 | 所有 API 统一入口 |
| Prometheus | http://localhost:9090 | 指标采集与查询 |
| Grafana | http://localhost:3000 | 流量/性能监控大盘 |

## 📦 服务清单

### 微服务

| 服务 | 端口 | 说明 |
|-----|------|------|
| **gateway** | 8081 | API 网关，负责鉴权、限流、路由 |
| **auth-service** | 8085 | 用户认证、注册、资料管理 |
| **data-service** | 8082 | 数据源、清洗、融合、驾驶舱 |
| **agent-service** | 8083 | 智能问答、LangChain 编排 |
| **config-service** | 8084 | 配置管理、阈值设置 |

### 基础设施

| 服务 | 端口 | 说明 |
|-----|------|------|
| mysql | 3307 | 关系数据库 |
| redis | 6379 | 缓存与会话存储 |
| nginx | 80/443 | 反向代理（Docker) |

## 🏗️ 架构亮点

### ✅ 接口化编程（DIP - Dependency Inversion Principle）
所有微服务均采用接口-实现分离设计：

| 服务 | 接口数 | 编译状态 |
|-----|--------|--------|
| auth-service | 2 | ✅ BUILD SUCCESS |
| config-service | 1 | ✅ BUILD SUCCESS |
| data-service | 5 | ✅ BUILD SUCCESS |
| gateway | 2 | ✅ BUILD SUCCESS |
| agent-service | 5 (Python ABC) | ✅ Python syntax OK |

**详见**：[代码架构与设计模式](docs/07-code-architecture.md)

### ✅ 设计模式应用
- **Strategy Pattern**：多 LLM 提供商无缝切换
- **Factory Pattern**：动态创建 LLM 和检索实例
- **Adapter Pattern**：JWT 和限流逻辑隔离

### ✅ SOLID 原则
- **S**：单一职责 - 每个接口一个专注用途
- **O**：开闭原则 - 扩展新实现无需修改现有代码
- **L**：里氏替换 - 所有实现可互换
- **I**：接口隔离 - 接口仅包含需要的方法
- **D**：依赖倒置 - 依赖接口，不依赖实现

## 🛠️ 开发指南

### 新增微服务步骤

1. **定义接口**（在 `service/` 下）
   ```java
   public interface IMyService {
       SomeResult doSomething();
   }
   ```

2. **实现接口**（同包或 `impl/` 子包）
   ```java
   @Service
   public class MyServiceImpl implements IMyService {
       @Override
       public SomeResult doSomething() { ... }
   }
   ```

3. **依赖注入**（控制器中）
   ```java
   @RestController
   public class MyController {
       private final IMyService myService;
       
       public MyController(IMyService myService) {
           this.myService = myService;
       }
   }
   ```

4. **编译验证**
   ```bash
   mvn clean compile -q
   ```

详见：[代码架构与设计模式 - 4. 依赖注入与服务链路](docs/07-code-architecture.md#4-依赖注入与服务链路)

### 单元测试模板

```java
class MyServiceTest {
    private IMyService myService;
    
    @Before
    public void setup() {
        // Mock 依赖
        IDownService mockDownService = mock(IDownService.class);
        
        // 注入 mock 到实现类
        myService = new MyServiceImpl(jdbcTemplate, mockDownService);
    }
    
    @Test
    public void testDoSomething() {
        // 测试代码...
    }
}
```

## 📋 编译与验证

### Java 服务

```bash
# 逐个编译
cd services/auth-service && mvn clean compile -q
cd services/config-service && mvn clean compile -q
cd services/data-service && mvn clean compile -q
cd services/gateway && mvn clean compile -q

# 或全量编译
mvn clean compile -q -DskipTests
```

### Python 服务

```bash
cd services/agent-service
python -m py_compile app/services/iagent.py
python -m py_compile app/services/isession.py
python -m py_compile app/services/idashboard.py
python -m py_compile app/services/agent_impl.py
```

## 📊 项目统计

| 指标 | 数值 |
|-----|------|
| Java 接口 | 10 个 |
| Python ABC 接口 | 5 个 |
| 微服务 | 5 个 |
| 前端组件 | 20+ 个 |
| 代码行数 | ~15,000+ |
| 单元测试 | 支持 Mock |

## 🔐 安全特性

- ✅ JWT 认证与授权
- ✅ 限流防护（幅度窗口算法）
- ✅ 密码加密存储（BCrypt）
- ✅ HTTPS + TLS 1.3
- ✅ SQL 注入防护（Prepared Statement）
- ✅ CORS 跨域配置

## 📈 性能优化

- ✅ Redis 缓存（驾驶舱数据、会话、配额）
- ✅ 异步任务队列（清洗/融合任务）
- ✅ 数据库连接池（HikariCP）
- ✅ 前端代码分割（Vite + Vue）
- ✅ 前端懒加载（Lazy Loading）

## 🤝 贡献指南

1. Fork 此项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📝 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE)

## 🙋 支持与帮助

- 📖 查看文档：[docs/README.md](docs/README.md)
- 💬 讨论问题：提交 Issue
- 🔧 代码架构问题：阅读 [代码架构与设计模式](docs/07-code-architecture.md)

---

**最后更新**：2026-03-17  
**当前版本**：1.0.0  
**架构模式**：微服务 + 接口化编程 (DIP/SOLID)

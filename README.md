# 📚 图书管理系统 Library Management System

![Java](https://img.shields.io/badge/Java-11%2B-blue)
![Dependencies](https://img.shields.io/badge/dependencies-Gson%202.14.0-green)
![Framework](https://img.shields.io/badge/web%20framework-none-lightgrey)

一个用**纯 Java** 实现的图书管理系统，**命令行控制台**与**浏览器网页**双前端，共用同一套业务逻辑与同一份数据文件。

> 「面向对象程序设计」课程设计 · 第 8 组

- 后端不依赖任何 Web 框架，使用 JDK 自带的 `com.sun.net.httpserver.HttpServer`
- 前端不用任何框架和构建工具，`web/index.old.html` 一个文件即完整单页应用
- 数据用 JSON 文件持久化，整个项目只有 **Gson 一个外部依赖**
- 核心目标是把「分层设计」「接口鉴权」「并发安全」「防御性编程」落到可运行的代码里

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [更新](#)
- [快速开始](#快速开始)
- [默认账号](#默认账号)
- [项目结构](#项目结构)
- [系统架构](#系统架构)
- [HTTP 接口文档](#http-接口文档)
- [数据存储](#数据存储)
- [关键实现说明](#关键实现说明)
- [已知限制与后续计划](#已知限制与后续计划)
- [Git 提交规范](#git-提交规范)

---

## 功能特性

### 读者
- 注册、登录、退出（token 鉴权）
- 浏览全部图书，按书名 / 作者 / 编号实时搜索
- **借书时自选借期**：1～365 天，默认 30 天，提供 7 / 15 / 30 / 60 / 90 天快捷按钮，输入时实时显示应还时间
- **我的借阅**面板：只列未归还的书，按「快到期 → 已逾期」排序，用红/绿标签显示还剩几天或逾期几天
- 一键归还，借完 / 没借过的书按钮自动置灰

### 管理员
- 图书的增、删、改、查（含多册管理：总数 / 可借数）
- 查看**全部**读者的借阅记录（读者只能看到自己的，即数据隔离）
- 用户管理：查看用户列表、升降角色（ADMIN / READER）、重置他人密码

### 业务规则约束
| 规则 | 行为 |
|---|---|
| 只有管理员能增删改图书 / 管理用户 | 非管理员请求返回 `403` |
| 有册数在外借的图书不能删除 | 返回失败提示，附在外借册数 |
| 不能取消自己的管理员身份 | 返回 `400` |
| 不能把最后一个管理员降为读者 | 返回失败提示 |
| 可借数永远不能大于总数 | `available` 由服务端赋值，客户端伪造无效 |
| 借期超出范围自动收敛 | ≤0 或非数字按 30 天，>365 按 365 天 |
| 用户名 / 姓名 / 密码格式校验 | 用户名仅字母数字；姓名仅汉字或字母；密码至少 6 位 |

---

## 技术栈

| 层面 | 采用方案 | 说明 |
|---|---|---|
| 语言 | Java（JDK 11+，开发环境 JDK 21） | 用到 `Files.writeString`、`readAllBytes` 等 Java 11+ API |
| HTTP 服务器 | `com.sun.net.httpserver.HttpServer` | JDK 内置，零依赖 |
| JSON | [Gson 2.14.0](https://github.com/google/gson) | 唯一外部依赖，已随仓库提供 `lib/gson-2.14.0.jar` |
| 持久化 | JSON 文件 | 无数据库，采用「临时文件 + 原子改名」写入 |
| 前端 | 原生 HTML / CSS / JavaScript | `fetch` + `async/await`，无框架、无构建 |
| 并发 | `synchronized` + 固定线程池 | 见[关键实现说明](#关键实现说明) |

---

## 更新

### index.html
- 更新了新的前端页面
- 旧版index.html文件 上传为index.old.html
- 旧版页面如下：
  - 登录界面：
  ![ver1.login.png](web/version_1%20images/ver1.login.png)
  - 注册界面：
  ![ver1.register.png](web/version_1%20images/ver1.register.png)
  - 我的借阅界面：
  ![ver1.myrecord.png](web/version_1%20images/ver1.myrecord.png)
  - 图书界面：
  ![ver1.listbook.png](web/version_1%20images/ver1.listbook.png)
  - 借阅记录界面：
  ![ver1.borrowrecord.png](web/version_1%20images/ver1.borrowrecord.png)
  - 用户管理界面：
  ![ver1.usermanagement.png](web/version_1%20images/ver1.usermanagement.png)

---

## 快速开始

### 1. 环境要求
- **JDK 11 或更高**（`java -version` 能打印版本即可）
- 不需要装 Maven / Gradle，Gson jar 已在 `lib/` 目录内

### 2. 编译

在**项目根目录**（含 `src/`、`web/`、`lib/` 的那一层）执行：

```bash
javac -encoding UTF-8 -cp "lib/gson-2.14.0.jar" -d out src/*.java
```

> [!IMPORTANT]
> **`-encoding UTF-8` 不能省。**
> Windows 下 `javac` 默认按系统 ANSI 编码读源码，而本项目源码是 UTF-8（含大量中文注释与字符串），
> 不加会直接报 `错误: 编码 UTF-8 的不可映射字符`。

### 3. 启动网页版（推荐）

```bash
# Linux / macOS
java -cp "out:lib/gson-2.14.0.jar" Server

# Windows（classpath 分隔符是分号）
java -cp "out;lib/gson-2.14.0.jar" Server
```

启动成功会打印：

```
服务器已启动：
  GET  http://localhost:8080
  POST http://localhost:8080/api/login   {"username":"admin","password":"admin123"}
  ...
```

浏览器打开 <http://localhost:8080> 即可。

> **必须在项目根目录启动**：程序用相对路径读取 `web/` 目录和 `books.json` 等数据文件，
> 换目录运行会 404 或读不到数据。

### 4. 启动控制台版（可选）

```bash
# Linux / macOS
java -cp "out:lib/gson-2.14.0.jar" Main

# Windows
java -cp "out;lib/gson-2.14.0.jar" Main
```

控制台版包含完整的 9 项菜单（增、删、改、查、显示全部、借书、还书、查看记录、用户管理），
未登录时提供登录 / 注册入口。

> [!NOTE]
> 控制台版与网页版是**两个独立进程**，各自在内存里持有一份数据副本，
> 同时修改同一份文件时遵循「后写入者覆盖」。演示时建议只运行其中一个。

---

## 默认账号

首次启动时若 `users.json` 不存在或为空，程序会自动创建一个管理员：

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN |

仓库内的演示数据中还附带了一个读者账号 `zhang3`（密码为课程演示时设置，`users.json` 中存的是哈希值）。

> [!WARNING]
> 仓库中已提交了一份 `users.json`，其中包含上述演示账号，因此启动时**不会再触发**默认管理员创建逻辑 ——
> 也就是说仓库里这份密码就是所有人拿到的那份。**登录后请立即修改密码。**
> 若要把本项目真正投入使用，请将 `users.json` 加入 `.gitignore` 并更换全部账号。

首次启动时若 `books.json` 不存在，会自动灌入 10 本演示图书。仓库内已附带了 **51 本**演示图书与 **15 条**借阅记录，clone 后即可直接演示。

---

## 项目结构

```
library-management-system/
├── src/
│   ├── Book.java                 图书实体（编号 / 书名 / 作者 / 总数 / 可借数）
│   ├── BorrowRecord.java         借阅记录实体（含借期天数、应还时间、逾期天数计算）
│   ├── User.java                 用户实体（用户名 / 密码哈希 / 姓名 / 角色）
│   ├── LibraryService.java       ★ 业务逻辑层（唯一操作数据的类）
│   ├── Main.java                 控制台前端
│   └── Server.java               HTTP 服务器：路由、鉴权、静态资源
├── web/
│   └── index.html                网页前端（单文件，内含全部 CSS 与 JS）
├── lib/
│   └── gson-2.14.0.jar           唯一外部依赖
├── books.json                    图书数据（演示数据，已提交）
├── borrows.json                  借阅记录（演示数据，已提交）
├── users.json                    用户数据（演示数据，已提交）
└── .gitignore
```

---

## 系统架构

```
      浏览器（web/index.html）          控制台（Main.java）
                  │                              │
                  ▼                              ▼
            Server.java                     Main.java          ← 前端层：管「怎么问、怎么显示」
        （路由 / 鉴权 / 静态资源）      （Scanner 菜单交互）
                  │                              │
                  └──────────────┬───────────────┘
                                 ▼
                        LibraryService.java                   ← 业务层：管「能不能做、做了会怎样」
                                 ▼
                 Book / User / BorrowRecord                   ← 实体层：管「一个东西长什么样」
                                 ▼
              books.json / borrows.json / users.json          ← 持久化：JSON 文件
```

**两条贯穿全局的边界约束：**

1. **`LibraryService` 不认识 HTTP，也不认识 token。**
   它的每个方法只接收一个 `User operator` 参数，表示「谁在操作」。
   「怎么从 HTTP 请求里认出这个人」完全属于 `Server` 的职责 —— 这正是同一套业务逻辑能被两个前端复用的原因。
2. **`Server` 从不直接读写数据文件，`LibraryService` 从不接触 `HttpExchange`。**
   新增功能时先判断它属于哪一层。

---

## HTTP 接口文档

接口统一挂在 `/api/` 下，静态资源（`/`、`/index.html`）由 `web/` 目录直接提供。
除登录、注册外，所有接口均需携带 token。

### 如何携带 token

登录成功后返回的 token 有两种传递方式，任选其一：

```http
Authorization: Bearer 0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01
```

或作为查询参数（**仅建议调试使用**，token 会进入浏览器历史与服务器日志）：

```
GET /api/books?token=0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01
```

网页前端将 token 存放在 `sessionStorage`，刷新页面仍有效、关闭标签页即失效。

### 接口一览

| 方法 | 路径 | 说明 | 需登录 | 需管理员 |
|---|---|:--|:--:|:--:|
| `GET` | `/hello` | 连通性测试，返回纯文本 | ❌ | ❌ |
| `POST` | `/api/login` | 登录，请求 `{username, password}`，返回 `{token}` | ❌ | ❌ |
| `POST` | `/api/register` | 注册 `{username, password, name}` | ❌ | ❌ |
| `POST` | `/api/logout` | 退出，作废当前 token | ✅ | ❌ |
| `GET` | `/api/me` | 当前登录用户（不含密码） | ✅ | ❌ |
| `GET` | `/api/books` | 全部图书 | ✅ | ❌ |
| `GET` | `/api/books/{id}` | 单本图书，不存在返回 `404` | ✅ | ❌ |
| `POST` | `/api/books` | 新增图书 `{bookId, bookName, author, total}` | ✅ | ✅ |
| `PUT` | `/api/books/{id}` | 修改书名 / 作者 `{bookName, author}` | ✅ | ✅ |
| `DELETE` | `/api/books/{id}` | 删除图书（有册在外借时拒绝） | ✅ | ✅ |
| `GET` | `/api/borrows` | 借阅记录（管理员看全部，读者仅本人） | ✅ | ❌ |
| `GET` | `/api/borrows/mine` | 我的借阅记录 | ✅ | ❌ |
| `POST` | `/api/borrows` | 借书 `{bookId, days}` | ✅ | ❌ |
| `POST` | `/api/borrows/return` | 还书 `{bookId}` | ✅ | ❌ |
| `GET` | `/api/users` | 用户列表（不含密码） | ✅ | ✅ |
| `PUT` | `/api/users/{u}/role` | 修改角色 `{role: "ADMIN" \| "READER"}` | ✅ | ✅ |
| `PUT` | `/api/users/{u}/password` | 重置密码 `{password}` | ✅ | ✅ |

错误响应统一为 `{"message": "中文提示"}`。

### 状态码约定

| 码 | 含义 |
|---|---|
| `200` | 成功 |
| `400` | 请求体不合法（非 JSON / 缺字段 / 取值不合法） |
| `401` | 未登录，或 token 已失效 |
| `403` | 已登录但权限不足（例如读者试图删除图书），或静态资源路径非法 |
| `404` | 路径或资源不存在 |
| `405` | 该路径不支持此 HTTP 方法 |
| `409` | 冲突（注册时用户名已被占用） |
| `500` | 服务器内部错误 |

### 调用示例

```bash
# 1. 登录换取 token
curl -X POST http://localhost:8080/api/login \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
# → {"token":"0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01"}

# 2. 携带 token 查询图书列表
curl http://localhost:8080/api/books \
     -H "Authorization: Bearer 0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01"

# 3. 借阅 B001，借期 7 天
curl -X POST http://localhost:8080/api/borrows \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer 0f29fae1-..." \
     -d "{\"bookId\":\"B001\",\"days\":7}"

# 4. 归还 B001
curl -X POST http://localhost:8080/api/borrows/return \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer 0f29fae1-..." \
     -d "{\"bookId\":\"B001\"}"
```

---

## 数据存储

三个 JSON 文件分别对应三种实体，全部位于项目根目录，程序启动时读入内存、每次写操作后整体回写。

### `books.json`
```json
[
  { "bookId": "B001", "bookName": "Java核心技术", "author": "Horstmann", "total": 3, "available": 2 }
]
```
- `total`：馆藏总册数；`available`：当前可借册数
- 在外借册数 = `total - available`，是**算出来的**，不单独存字段，避免数据不一致

### `borrows.json`
```json
[
  { "bookId": "B001", "bookName": "Java核心技术", "borrower": "admin",
    "borrowTime": 1791900243179, "returnTime": 0, "days": 7 }
]
```
- `borrowTime` / `returnTime`：**毫秒时间戳**（`System.currentTimeMillis()`）
- `returnTime == 0` 表示**尚未归还**，是判断借阅状态的关键约定
- `days`：借期天数。**历史数据没有该字段**，读回为 `0`，程序按默认 30 天兜底
- `borrower` 存**用户名**（不是姓名），保证记录能精确关联到账号

### `users.json`
```json
[
  { "username": "admin",
    "password": "8ccb1b5197d2d40f2141cb558cffad5cd82cab2e355aea2de57b0f2c40c8b526",
    "name": "管理员", "role": "ADMIN" }
]
```
- `password` 为 `SHA-256("libms::" + 明文)` 的十六进制字符串，**不存明文**
- `role` 取值只能是 `"ADMIN"` 或 `"READER"`

> [!NOTE]
> **关于时间戳而非格式化字符串**：排版属于「展示」，不该混进「数据」。
> 存毫秒数后，换个显示格式（`yyyy-MM-dd HH:mm`）不需要动数据。
>
> **关于演示数据进仓库**：本项目定位为课程实训，三个 JSON 文件被**故意提交**，
> 让 clone 下来的人立刻有数据可演示。代价是：本机运行后 `git status` 会持续显示这三个文件有改动，
> 想丢弃本地改动可执行 `git checkout -- books.json borrows.json users.json`。

---

## 关键实现说明

### 1. 分层与复用
实体层 / 业务层 / 前端层严格分离，业务层是唯一的「数据守门人」。
`Server` 负责把 HTTP 请求翻译成「一个 `User` + 一组参数」，`Main` 负责把 `Scanner` 输入翻译成同样的东西，
两者调用的是同一批方法，行为天然一致，不存在「网页版能借、控制台版不能借」这类分叉。

### 2. 鉴权与权限
- 登录成功生成 `UUID` 作为 token，存入内存 `Map<String, User> sessions`
- 每次请求先从 `Authorization: Bearer` 或 `?token=` 提取 token，取回用户；取不到即 `401`
- 写操作（增删改图书、用户管理）在**进入业务逻辑之前**就统一拦截非管理员，返回 `403`；
  业务层内部仍会再校验一次角色作为最终防线
- 对外输出的用户对象经 `publicUser()` 过滤，**只暴露 `username` / `name` / `role`，密码哈希永不出网**

### 3. 并发安全
不配置线程池时，JDK 的 `HttpServer` 只用一个线程串行处理所有请求 —— 这不是「安全」，而是「慢到掩盖了并发 bug」。
本项目做了三件事：

| 措施 | 位置 | 解决的问题 |
|---|---|---|
| `setExecutor(newFixedThreadPool(16))` | `Server.java` | 让服务器具备真正的并发处理能力 |
| 业务层方法全部 `synchronized`；查询类方法**返回集合副本** | `LibraryService.java` | 防止遍历共享 `ArrayList` 时被其他线程修改而抛 `ConcurrentModificationException` |
| `writeAtomic()`：先写 `.tmp` 再 `Files.move` 原子改名 | `LibraryService.java` | 防止两个线程同时写文件产生半截 JSON，导致**静默丢数据** |

> `synchronized` 相当于用一把大锁串行化整个业务层，简单且绝对正确，对本项目的数据规模完全够用。
> 若要追求更高吞吐，可替换为 `ReentrantReadWriteLock` 或 `ConcurrentHashMap`。

### 4. 其它防御性细节
- **静态资源防目录穿越**：`web.resolve(path).normalize()` 后校验 `startsWith(webRoot)`，越界请求返回 `403`
- **统一异常兜底**：所有 handler 都用 `safe()` 包装，JSON 解析失败回 `400`，其它异常回 `500` 的 JSON，
  而不是直接掐断连接
- **前端 XSS 防护**：所有由服务器返回的数据一律通过 `textContent` 渲染，不使用 `innerHTML`
- **前端 401 处理**：任意请求返回 `401` 自动清空 token 并跳回登录页

---

## 已知限制与后续计划

### 已知限制

| # | 问题 | 影响 | 改进方向 |
|---|---|---|---|
| 1 | 业务失败也返回 `HTTP 200` | 前端无法仅凭状态码判断成败 | 业务层返回带状态码的结果对象 |
| 2 | 密码哈希强度不足 | 固定盐 `libms::` + 单轮 SHA-256，相同密码哈希相同，可被 GPU 快速爆破 | 改用 `PBKDF2WithHmacSHA256` + 每用户随机盐 + 迭代 |
| 3 | token 永不过期 | `sessions` 只增不减，进程不重启则长期有效 | 增加过期时间与定时清理 |
| 4 | 同一用户可重复借同一本书 | 一人可借走某本书的全部册数 | 借书前校验是否已持有 |
| 5 | 并发「检查—执行」窗口 | `canBorrow()` 与 `borrowOne()` 之间理论上可被插入 | 在业务层把校验与扣减合并为不可分割的原子操作 |
| 6 | 端口 `8080` 硬编码 | 端口被占用时需改源码 | 从命令行参数或环境变量读取 |
| 7 | 缺少自动化测试 | 回归只能靠手工点击 | 为 `LibraryService` 补充 JUnit 测试 |
| 8 | `Server.java` 单文件约 450 行 | 路由持续增长后难以维护 | 按资源拆分为多个 `Handler` 类 |

### 后续计划
- 续借功能（在原借期基础上延长）
- 首页数据概览（总藏书量 / 在借数 / 逾期数）
- 借阅记录导出 CSV、列表分页与排序
- 预约排队：图书借完时可排队，归还后自动通知
- 用 H2 / SQLite 替换 JSON 文件持久化
- 引入日志框架，替换 `System.out.println`

---

## Git 提交规范

提交信息采用 [Conventional Commits](https://www.conventionalcommits.org/) 风格，便于快速识别改动类型：

| 前缀 | 用途 |
|---|---|
| `feat:` | 新功能 |
| `fix:` | 修复 bug |
| `refactor:` | 重构（不改变外部行为） |
| `docs:` | 仅修改文档 |
| `style:` | 仅修改格式（空格、换行） |
| `test:` | 增删测试 |
| `chore:` | 杂项（`.gitignore`、依赖升级等） |

示例：

```
feat: 借书时支持自选借期（1~365 天）
fix: 配置 HttpServer 线程池后并发写导致的 JSON 截断
docs: 补充 HTTP 接口文档与状态码约定
```

---

## 许可

本项目为课程实训作品，仅供学习交流，可自由取用。

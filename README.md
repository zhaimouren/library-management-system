# 📚 图书管理系统 Library Management System

一个用 **纯 Java（不依赖任何 Web 框架）** 手写的图书管理系统：JDK 自带的 `com.sun.net.httpserver.HttpServer` 做后端，原生 HTML + CSS + JavaScript 做前端，两个前端（命令行控制台 / 浏览器网页）共用同一套业务逻辑和同一份数据文件。

> 这个项目的定位是**学 Java**：没有用 Spring、没有用 Maven、没有用数据库，所有东西都摊开在源码里，从「一个 `.java` 文件怎么变成能响应 HTTP 请求的服务器」开始，一层一层自己搭。

---

## 目录

- [功能](#功能)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [默认账号](#默认账号)
- [HTTP 接口文档](#http-接口文档)
- [项目结构](#项目结构)
- [数据文件格式](#数据文件格式)
- [并发安全](#并发安全)
- [用 Git 管理这个项目](#用-git-管理这个项目)
- [已知问题与后续计划](#已知问题与后续计划)

---

## 功能

### 读者
- 注册 / 登录 / 退出
- 浏览全部图书、搜索、查看单本详情
- 借书时**自选借阅天数**（1～365 天，默认 30 天，带 7/15/30/60/90 快捷按钮）
- 查看**我的借阅**（只列未归还的，按「快到期 / 已逾期」排在最前，显示还剩几天或逾期几天）
- 一键归还

### 管理员
- 图书的增 / 删 / 改 / 查（册数、可借数由服务器维护，客户端改不动）
- 查看**全部**借阅记录
- 用户管理：查看用户列表、修改角色（ADMIN / READER）、重置他人密码
- 保护规则：管理员不能取消自己的管理员身份，也不能删掉最后一个管理员

### 两个前端
- **控制台版**（`Main.java`）：完整的 9 项菜单，学生作业形态
- **网页版**（`web/index.html`）：单页应用，带登录框、图书表格、借阅弹窗、我的借阅面板、用户管理面板

---

## 技术栈

| 层面 | 用了什么 | 说明 |
|---|---|---|
| 语言 | Java（测试于 JDK 21，需 **JDK 11+**） | 用了 `Files.writeString`、`InputStream.readAllBytes` 等 Java 11+ API |
| HTTP 服务器 | `com.sun.net.httpserver.HttpServer` | JDK 自带，无需任何依赖 |
| JSON | [Gson 2.14.0](https://github.com/google/gson)（`lib/gson-2.14.0.jar`） | 唯一的外部依赖 |
| 持久化 | JSON 文件（`books.json` / `borrows.json` / `users.json`） | 没有数据库；用「写临时文件再改名」保证原子性 |
| 前端 | 原生 HTML / CSS / JavaScript（`fetch` + `async/await`） | 没有框架、没有构建工具，一个文件就是一个页面 |
| 并发 | `synchronized` + 固定线程池 | 见[并发安全](#并发安全) |

---

## 快速开始

### 1. 环境要求
- **JDK 11 或更高**（命令行执行 `java -version` 能看到版本号即可）
- **Gson jar**：项目已经带在 `lib/gson-2.14.0.jar`

### 2. 编译

> ⚠️ Windows 的 `javac` 默认用系统 ANSI 编码读源码，而本项目源码是 **UTF-8**（含大量中文）。
> 不加 `-encoding UTF-8` 会报 `错误: 编码 UTF-8 的不可映射字符`。

```bash
# 在项目根目录执行（注意 Windows 用 ; 分隔 classpath，Linux/macOS 用 :）
javac -encoding UTF-8 -cp "lib/gson-2.14.0.jar" -d out src/*.java
```

### 3. 启动网页版服务器

```bash
java -cp "out;lib/gson-2.14.0.jar" Server
```

看到这几行就说明起来了：

```
图书管理系统 · 服务器
  GET  http://localhost:8080
  POST http://localhost:8080/api/login
  ...
```

然后浏览器打开 <http://localhost:8080>。

### 4. 启动控制台版（可选）

```bash
java -cp "out;lib/gson-2.14.0.jar" Main
```

> 两个前端可以同时跑，但它们各自在内存里持有一份数据副本，
> 同时修改同一个文件时是「后写的赢」。生产环境应该只有一个写入口。

---

## 默认账号

首次启动时如果 `users.json` 不存在或为空，服务器会自动创建一个管理员：

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN |

> 🔐 **第一次登录后请立刻改掉密码。** 也请确认 `.gitignore` 里排除了 `users.json`。

首次启动时如果 `books.json` 不存在，会灌入 10 本演示图书。

---

## HTTP 接口文档

所有接口都在 `/api/` 下。除了登录和注册，其余接口都需要带票。

### 怎么带票

登录成功后返回的 `token` 有两种递法（可选其一）：

```http
Authorization: Bearer 0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01
```

或者在地址栏里（**仅用于调试**，因为 token 会进浏览器历史和服务器日志）：

```
GET /api/books?token=0f29fae1-...
```

### 接口一览

| 方法 | 路径 | 说明 | 要登录 | 要管理员 |
|---|---|:--|:--:|:--:|
| `POST` | `/api/login` | 登录，返回 `{"token":"..."}` | ❌ | ❌ |
| `POST` | `/api/register` | 注册 `{username, password, name}` | ❌ | ❌ |
| `POST` | `/api/logout` | 退出，作废当前 token | ✅ | ❌ |
| `GET` | `/api/me` | 当前登录用户（**不含密码**） | ✅ | ❌ |
| `GET` | `/api/books` | 全部图书 | ✅ | ❌ |
| `GET` | `/api/books/{id}` | 单本图书，不存在返回 404 | ✅ | ❌ |
| `POST` | `/api/books` | 新增图书 `{bookId, bookName, author, total}` | ✅ | ✅ |
| `PUT` | `/api/books/{id}` | 修改书名 / 作者 `{bookName, author}` | ✅ | ✅ |
| `DELETE` | `/api/books/{id}` | 删除图书（有书在外借时拒绝） | ✅ | ✅ |
| `GET` | `/api/borrows` | 借阅记录（管理员看全部，读者只看自己） | ✅ | ❌ |
| `GET` | `/api/borrows/mine` | 我的借阅记录 | ✅ | ❌ |
| `POST` | `/api/borrows` | 借书 `{bookId, days}` | ✅ | ❌ |
| `POST` | `/api/borrows/return` | 还书 `{bookId}` | ✅ | ❌ |
| `GET` | `/api/users` | 用户列表（**不含密码**） | ✅ | ✅ |
| `PUT` | `/api/users/{u}/role` | 改角色 `{role:"ADMIN"\|"READER"}` | ✅ | ✅ |
| `PUT` | `/api/users/{u}/password` | 重置密码 `{password}` | ✅ | ✅ |

### 状态码约定

| 码 | 含义 |
|---|---|
| `200` | 成功 |
| `400` | 请求体格式不对（不是合法 JSON / 缺字段 / 值不合法） |
| `401` | 没登录，或 token 已失效 |
| `403` | 登录了，但权限不够（比如读者想删书） |
| `404` | 找不到（路径不对，或 id 不存在） |
| `405` | 这个路径不支持这个 HTTP 方法 |
| `409` | 冲突（注册时用户名已被占用） |
| `500` | 服务器内部错误 |

### 示例

```bash
# 登录拿票
curl -X POST http://localhost:8080/api/login \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
# → {"token":"0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01"}

# 带票查书
curl http://localhost:8080/api/books \
     -H "Authorization: Bearer 0f29fae1-8c1b-4d5e-9a3f-2b6c7d8e9f01"

# 借 7 天
curl -X POST http://localhost:8080/api/borrows \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer 0f29fae1-..." \
     -d "{\"bookId\":\"B001\",\"days\":7}"
```

---

## 项目结构

```
Library Management System/
├── src/                             ← 源码
│   ├── Book.java                    图书实体（id / 书名 / 作者 / 总数 / 可借数）
│   ├── BorrowRecord.java            借阅记录（含借期天数，可算应还时间与逾期天数）
│   ├── User.java                    用户实体（用户名 / 密码哈希 / 姓名 / 角色）
│   ├── LibraryService.java          ★ 业务逻辑层（唯一碰数据的地方）
│   ├── Main.java                    控制台前端
│   ├── Server.java                  HTTP 服务器 + 路由 + 鉴权（网页版后端）
│   └── JsonTest.java                学习用的小实验，可删
├── web/
│   └── index.html                   网页前端（单文件，含全部 CSS / JS）
├── lib/
│   └── gson-2.14.0.jar              唯一的外部依赖
├── backup/                          控制台版导出的纯文本报表
├── books.json                       图书数据（运行时生成，不进仓库）
├── borrows.json                     借阅数据（运行时生成，不进仓库）
├── users.json                       用户数据（运行时生成，不进仓库）
└── .gitignore
```

### 分层设计

```
   浏览器               控制台
      │                    │
      ▼                    ▼
  Server.java          Main.java        ← 前端层：只管「怎么问、怎么展示」
      │                    │
      └────────┬───────────┘
               ▼
       LibraryService.java               ← 业务层：只管「能不能干、干了会怎样」
               ▼
    Book / User / BorrowRecord           ← 实体层：只管「一个东西长什么样」
               ▼
        books.json / borrows.json / users.json
```

**两条关键边界：**

1. **`LibraryService` 不认识 HTTP，也不认识 token。**
   它只收一个 `User operator`（谁在操作）。「怎么从 HTTP 请求里认出这个人」是 `Server` 的事。
   —— 这就是为什么同一套业务逻辑能被控制台和网页两个前端共用。

2. **`Server` 永远不碰数据文件，`LibraryService` 永远不碰 `HttpExchange`。**
   要加一个新功能，先问「这属于哪一层」。

---

## 数据文件格式

### `books.json`
```json
[
  {
    "bookId": "B001",
    "bookName": "Java核心技术",
    "author": "Cay S. Horstmann",
    "total": 3,
    "available": 2
  }
]
```

### `borrows.json`
```json
[
  {
    "bookId": "B001",
    "bookName": "Java核心技术",
    "borrower": "admin",
    "borrowTime": 1791900243179,
    "returnTime": 0,
    "days": 7
  }
]
```

- `borrowTime` / `returnTime`：**毫秒时间戳**（`System.currentTimeMillis()`）
- `returnTime == 0` 表示**还没还**
- `days`：借期天数。**老数据里没有这个字段**，读回来是 `0`，程序会自动按默认 30 天处理

> 💡 时间存的是毫秒数而不是 `"2026-10-21 11:22"` 这样的字符串，
> 是因为排版是**展示**的事，不该混进**数据**里。换一种显示格式不用改数据。

### `users.json`
```json
[
  {
    "username": "admin",
    "password": "8ccb1b5197d2d40f2141cb558cffad5cd82cab2e355aea2de57b0f2c40c8b526",
    "name": "管理员",
    "role": "ADMIN"
  }
]
```

- `password` 是 `SHA-256("libms::" + 明文)` 的十六进制字符串，**不是明文**
- `role` 只能是 `"ADMIN"` 或 `"READER"`

> 📌 **关于提交演示数据**
> 本项目是实训/学习项目，`books.json` / `borrows.json` / `users.json` 被**故意提交**进仓库 ——
> 别人 clone 下来直接就有 51 本书和一个可用账号（`admin` / `admin123`），不用自己造数据。
>
> 两个后果要知道：
> ① `users.json` 已存在时，程序**不会**再执行 `initDefaultAdmin()`，所以仓库里那份密码就是别人拿到的那份；
> ② 本机跑起来后这三个文件会被改写，`git status` 会一直显示它们有改动 —— 想丢掉本地改动用
> `git checkout -- books.json borrows.json users.json`。
>
> 如果哪天要把这个项目**真的拿去用**（而不是交作业），必须把这三个文件加进 `.gitignore`，
> 并把 `admin` 的密码改掉。

---

## 并发安全

这一节记录一个真实的、可复现的 bug 和它的修法。

### 问题

`HttpServer` 有两种模式：

| 写法 | 行为 |
|---|---|
| `HttpServer.create(addr, 0)`，不调 `setExecutor` | **只有一个线程**（名字叫 `HTTP-Dispatcher`）串行处理所有请求 |
| 加上 `server.setExecutor(Executors.newFixedThreadPool(16))` | 每个请求分配一个线程，真正的并发 |

实测：4 个各 `Thread.sleep(500)` 的请求，单线程模式下总共花了 **2120ms**（一个慢请求挡住了后面所有人），线程名全是 `HTTP-Dispatcher`。

**所以单线程的服务器不是"安全"，是"慢到掩盖了所有并发 bug"。** 一旦按常识把线程池配上，共享状态立刻开始打架。

### 压测结果

同一台机器，同一份业务代码，只是加不加 `synchronized` 的区别：

| | 请求结果 | 磁盘结果 |
|---|---|---|
| **原版** | `200 × 310` + 🔴 **`500 × 50`** | 60 本新增书只活下来 **48 本**（静默丢了 12 本） |
| **补丁版** | `200 × 360`（**零 500**） | 60 本**一本不丢** |

压测方法：300 个并发 `GET /api/books` + 60 个并发 `POST /api/books`。

原版 500 的正文是 `{"message":"服务器内部错误：null"}`，栈是：

```
java.util.ConcurrentModificationException
    at java.util.ArrayList$Itr.checkForComodification(ArrayList.java:1095)
    at com.google.gson.internal.bind.CollectionTypeAdapterFactory$Adapter.write(...)
    at com.google.gson.Gson.toJson(Gson.java:546)
    at Server.lambda$main$4(Server.java:150)          ← GET /api/books 里的 GSON.toJson(lib.allBooks())
```

### 两个独立的 bug

**① 遍历时被改（`ConcurrentModificationException`）**

`allBooks()` 原本直接 `return books;` —— 把内部那个活的 `ArrayList` 交了出去。
线程 A 正在用 Gson 遍历它，线程 B 执行 `books.add(...)`，迭代器立刻发现"结构被改了"并抛异常。

修法：**返回副本，不返回原件**。

```java
public synchronized List<Book> allBooks() {
    return new ArrayList<>(books);   // 给一本复印件
}
```

**② 写文件写到一半（静默丢数据）**

如果 `Files.writeString(path, json)` 正在把几千字符写进文件，另一个线程也来写同一个文件，
两次写入会交错，结果是一个**语法都不成立的半个 JSON** ——
更糟的是它不报错，下次启动 `load()` 时才发现数据没了。

修法：**写临时文件 + 原子改名**。

```java
private static void writeAtomic(Path target, String text, String errPrefix) throws IOException {
    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
    Files.writeString(tmp, text, StandardCharsets.UTF_8);
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
}
```

`Files.move` 在同一个文件系统上是**原子操作**：别的线程/进程要么看到旧文件，要么看到新文件，绝不会看到半个。

**③ 检查与执行之间的空隙（check-then-act）**

```java
if (!book.canBorrow()) { ... }   // 检查：还有可借的吗
book.borrowOne();                // 执行：可借数 -1
```

这两行之间可以插入别的线程。不过实测下来这个窗口只有几十纳秒 ——
用 `CyclicBarrier` 让 200 个线程在同一瞬间抢 1 册书，也几乎撞不上。
**它是真实存在的 bug，但极难触发**，而 ① 和 ② 是压一下就满地都是。

### 一共动了什么

`LibraryService.java` 加了 30 处 `synchronized`（所有 `public` 方法，外加三个访问器改成返回副本），
加了一个 `writeAtomic` 私有方法，`Server.java` 加了一行 `setExecutor`。

> ⚠️ `synchronized` 是"一把大锁锁住整个对象"：简单、绝对正确，但同一时刻只有一个请求能进业务层。
> 本书库这种规模完全够用。要更高吞吐再考虑 `ReentrantReadWriteLock` 或 `ConcurrentHashMap`。

---

## 用 Git 管理这个项目

### 0. 一次性配置（只需做一次）

```bash
git config --global user.name  "你的名字"
git config --global user.email "你的邮箱@example.com"
```

### 1. 在本机建仓库

在**项目根目录**（就是有 `src/`、`web/`、`books.json` 的那一层）打开终端：

```bash
git init                      # 把这个目录变成 Git 仓库，会生成隐藏的 .git/
git add .                     # 把当前所有文件放进「待提交」区
git status                    # ★ 先看一眼：确认 out/ 和 *.json 没被加进去
git commit -m "初始提交：图书管理系统（控制台 + 网页双前端）"
```

> `git status` 那一步很重要。本项目**故意提交** `books.json` / `borrows.json` / `users.json`（演示数据），
> 但 `out/`、`.idea/`、`backup/` **不应该**出现在列表里。
>
> 尤其 `backup/users.txt` 是控制台「导出报表」写的**明文密码** —— 它被 `backup/` 这条规则挡住了。
>
> 如果 `.gitignore` 看起来没生效，先检查它是不是被记事本存成了 `.gitignore.txt`。

### 2. 接到 GitHub

先在 <https://github.com/new> 上建一个**空仓库**（不要勾选 Add README / .gitignore / license）。

```bash
git branch -M main                                       # 把默认分支名改成 main
git remote add origin https://github.com/你的用户名/library-management-system.git
git push -u origin main
```

第一次 `push` 会弹窗让你登录 GitHub（推荐用浏览器授权，或改用 SSH）。

### 3. 以后的日常

```bash
git status                    # 看看改了什么
git diff                      # 看具体改了哪几行
git add src/Server.java       # 只提交这一个文件
git commit -m "feat: 借书时可以自选天数"
git push
```

### 4. 提交信息怎么写

推荐 [Conventional Commits](https://www.conventionalcommits.org/) 风格，一眼能看出这次提交干了什么类型的活：

| 前缀 | 用在 |
|---|---|
| `feat:` | 新功能 |
| `fix:` | 修 bug |
| `refactor:` | 改结构但不改行为 |
| `docs:` | 只改文档 |
| `style:` | 只改格式（空格、换行） |
| `test:` | 加测试 |
| `chore:` | 杂活（改 .gitignore、升依赖） |

例：`fix: 给 HttpServer 配线程池后并发写导致的 JSON 截断`

### 5. 如果密码已经被提交过

**改代码没用 —— 历史里的那个文件还在。** 必须：

1. 立刻改掉所有涉及的密码
2. 用 [`git filter-repo`](https://github.com/newren/git-filter-repo) 把该文件从**全部历史**里剔除
3. 强推：`git push --force`
4. 如果是公开仓库，视同已泄露

**所以 `.gitignore` 要在第一次 `git add` 之前就写好。**

---

## 已知问题与后续计划

### 已知问题

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | 业务失败也返回 `HTTP 200` | 前端无法从状态码区分成功/失败，只能看中文提示或重新拉数据 | 让业务层返回一个带状态码的结果对象 |
| 2 | 密码哈希太弱 | 全局固定盐 `"libms::"` + 裸 SHA-256 一遍，无迭代 → 相同密码哈希相同，且 GPU 可快速爆破 | 换 `PBKDF2WithHmacSHA256` + 每用户随机盐 + 20 万次迭代 |
| 3 | 同一用户可以重复借同一本书 | 一个人能把某本书的所有册数都借走 | 借书前查一下"你已经借了这本" |
| 4 | token 永不过期 | `sessions` 是个普通 `HashMap`，只增不减；进程不重启就一直有效 | 加过期时间 + 定时清理 |
| 5 | `POST /api/logout` 不校验请求方法 | 任何方法都能触发退出 | 加方法检查 |
| 6 | 端口 8080 硬编码 | 端口被占时要改源码 | 从命令行参数或环境变量读 |
| 7 | 没有自动化测试 | 每次改动全靠手工点 | 给 `LibraryService` 加 JUnit 测试 |
| 8 | `Server.java` 一个文件 450 行 | 路由越加越长 | 按资源拆成多个 Handler 类 |

### 后续想法

- 续借（在原有借期上延长）
- 首页概览（总藏书 / 在借 / 逾期统计）
- 导出借阅记录为 CSV
- 图书列表分页与排序
- 预约排队（书被借完时排队，归还后自动通知）
- 数据库（H2 / SQLite）替换 JSON 文件

---

## 许可

学习项目，随意取用。

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Server {

    private static final Path WEB_ROOT = Paths.get("web");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 登录请求的体型：{"username":"admin","password":"admin123"} */
    static class LoginRequest {
        String username;
        String password;
    }

    /** 注册请求的体型：{"username":...,"password":...,"name":...} */
    static class RegisterRequest {
        String username;
        String password;
        String name;
    }

    // 借书请求
    static class BorrowRequest {
        String bookId;
        int days;          // ← 新增这一行
    }

    // 角色请求
    static class RoleRequest {
        String role;
    }

    // 密码请求
    static class PasswordRequest {
        String password;
    }



    public static void main(String[] args) throws IOException {
        LibraryService lib = new LibraryService();
        lib.load();
        lib.seedIfEmpty();
        lib.loadBorrows();
        lib.loadUsers();
        lib.initDefaultAdmin();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        // ★★ 并发（第 11 步）：给 HttpServer 配一个固定大小的线程池。
        //    不写这一行时，JDK 只用【一个】线程（名字叫 HTTP-Dispatcher）串行处理所有请求 ——
        //    实测 4 个各睡 500ms 的请求总共花了 2120ms，一个慢请求会把后面所有人挡住。
        //    加上之后才是真正的并发服务器；代价是共享状态会同时被多个线程碰到，
        //    所以必须配合 LibraryService 里那些 synchronized 一起用。
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(16));

        server.createContext("/", safe(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                path = "/index.html";       // 访问根路径时默认给首页
            }

            // 关键：把 ../ 算掉，再检查有没有跑出 web 文件夹
            Path file = WEB_ROOT.resolve(path.substring(1)).normalize();
            if (!file.startsWith(WEB_ROOT.normalize())) {
                sendError(exchange, 403, "非法路径");
                return;
            }
            if (!Files.exists(file) || Files.isDirectory(file)) {
                sendError(exchange, 404, "找不到 " + path);
                return;
            }

            String contentType;
            if (path.endsWith(".html")) {
                contentType = "text/html; charset=utf-8";
            } else if (path.endsWith(".css")) {
                contentType = "text/css; charset=utf-8";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript; charset=utf-8";
            } else {
                contentType = "application/octet-stream";
            }

            send(exchange, 200, contentType, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }));

        server.createContext("/hello", safe(exchange ->
                send(exchange, 200, "text/plain; charset=utf-8", "你好，我是你的图书管理系统！")));

        // ① 登录：POST /api/login   体：{"username":...,"password":...}
        server.createContext("/api/login", safe(exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "请用 POST 请求登录");
                return;
            }
            LoginRequest req = GSON.fromJson(readBody(exchange), LoginRequest.class);
            if (req == null || req.username == null || req.password == null) {
                sendError(exchange, 400, "请求体必须是 {\"username\":...,\"password\":...}");
                return;
            }
            String token = lib.login(req.username, req.password);
            if (token == null) {
                sendError(exchange, 401, "用户名或密码错误");
                return;
            }
            send(exchange, 200, "application/json; charset=utf-8",
                    GSON.toJson(Map.of("token", token)));
        }));

        // ② 我是谁：带上票就能看到自己的信息
        server.createContext("/api/me", safe(exchange -> {
            User me = lib.getUserByToken(readToken(exchange));
            if (me == null) {
                sendError(exchange, 401, "未登录或登录已过期");
                return;
            }
            send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(publicUser(me)));
        }));

        // ③ 书库：GET 列表 / GET 一本 / POST 添加 / PUT 修改 / DELETE 删除
        server.createContext("/api/books", safe(exchange -> {
            User me = lib.getUserByToken(readToken(exchange));
            if (me == null) {
                sendError(exchange, 401, "未登录或登录已过期");
                return;
            }

            String method = exchange.getRequestMethod();
            // 路径里 "/api/books" 后面那一截："" 或 "/B001"
            String tail = exchange.getRequestURI().getPath().substring("/api/books".length());
            String id = tail.startsWith("/") ? tail.substring(1) : null;

            // ---------- 读：谁都能看 ----------
            if ("GET".equals(method)) {
                if (id == null) {
                    send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(lib.allBooks()));
                    return;
                }
                Book one = lib.findBook(id);
                if (one == null) {
                    sendError(exchange, 404, "没有这本书: " + id);
                    return;
                }
                send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(one));
                return;
            }

            // ---------- 写：先过网关 ----------
            if (!me.isAdmin()) {
                sendError(exchange, 403, "只有管理员能进行图书的增删改");
                return;
            }
            if (id != null && lib.findBook(id) == null) {
                sendError(exchange, 404, "没有这本书: " + id);
                return;
            }

            if ("POST".equals(method) && id == null) {
                Book req = GSON.fromJson(readBody(exchange), Book.class);
                if (req == null || req.getBookId() == null || req.getBookName() == null || req.getAuthor() == null) {
                    sendError(exchange, 400, "请求体必须是 {\"bookId\":...,\"bookName\":...,\"author\":...}");
                    return;
                }
                // 册数也听客户端的了：没填、填 0、填负数，一律当 1 册。
                // 注意 available 是拿 copies 赋的，不是从请求体里读的 ——
                // 所以客户端永远造不出「可借 > 总数」的书。
                int copies = req.getTotal() <= 0 ? 1 : req.getTotal();
                String msg = lib.addBook(me, new Book(req.getBookId(), req.getBookName(), req.getAuthor(), copies, copies));
                send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(Map.of("message", msg)));
                return;
            }

            if ("PUT".equals(method) && id != null) {
                Book req = GSON.fromJson(readBody(exchange), Book.class);
                if (req == null || req.getBookName() == null || req.getAuthor() == null) {
                    sendError(exchange, 400, "请求体必须是 {\"bookName\":...,\"author\":...}");
                    return;
                }
                String msg = lib.updateBook(me, new Book(id, req.getBookName(), req.getAuthor()));
                send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(Map.of("message", msg)));
                return;
            }

            if ("DELETE".equals(method) && id != null) {
                String msg = lib.deleteBook(me, id);
                send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(Map.of("message", msg)));
                return;
            }

            sendError(exchange, 405, "这个地址不支持 " + method + " 请求");
        }));

        // ④ 退出登录
        server.createContext("/api/logout", safe(exchange -> {
            lib.logout(readToken(exchange));
            send(exchange, 200, "application/json; charset=utf-8",
                    GSON.toJson(Map.of("message", "已退出登录")));
        }));

        server.createContext("/api/borrows", safe(exchange -> {
            User me = lib.getUserByToken(readToken(exchange));
            if (me == null) {
                sendError(exchange, 401, "未登录或登录已过期");
                return;
            }

            String method = exchange.getRequestMethod();
            String tail = exchange.getRequestURI().getPath().substring("/api/borrows".length());
            // tail 是 "" 或 "/return"

            // ---------- GET /api/borrows：看借阅记录 ----------
            if ("GET".equals(method) && tail.isEmpty()) {
                List<BorrowRecord> all = lib.allRecords();
                if (!me.isAdmin()) {
                    // 读者只能看自己的 —— 这叫「数据隔离」
                    List<BorrowRecord> mine = new ArrayList<>();
                    for (BorrowRecord r : all) {
                        if (me.getUsername().equals(r.getBorrower())) {
                            mine.add(r);
                        }
                    }
                    all = mine;
                }
                send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(all));
                return;
            }

            // ---------- GET /api/borrows/mine：只看我自己的（管理员也只看自己）----------
            if ("GET".equals(method) && "/mine".equals(tail)) {
                send(exchange, 200, "application/json; charset=utf-8",
                        GSON.toJson(lib.recordsOf(me.getUsername())));
                return;
            }

            // ---------- POST /api/borrows：借书 ----------
            if ("POST".equals(method) && tail.isEmpty()) {
                BorrowRequest req = GSON.fromJson(readBody(exchange), BorrowRequest.class);
                if (req == null || req.bookId == null || req.bookId.isEmpty()) {
                    sendError(exchange, 400, "请求体必须是 {\"bookId\":\"...\",\"days\":30}");
                    return;
                }
                send(exchange, 200, "application/json; charset=utf-8",
                        GSON.toJson(Map.of("message", lib.borrowBook(me, req.bookId, req.days))));
                return;
            }

            // ---------- POST /api/borrows/return：还书 ----------
            if ("POST".equals(method) && "/return".equals(tail)) {
                BorrowRequest req = GSON.fromJson(readBody(exchange), BorrowRequest.class);
                if (req == null || req.bookId == null || req.bookId.isEmpty()) {
                    sendError(exchange, 400, "请求体必须是 {\"bookId\":\"...\"}");
                    return;
                }
                send(exchange, 200, "application/json; charset=utf-8",
                        GSON.toJson(Map.of("message", lib.returnBook(me, req.bookId))));
                return;
            }

            sendError(exchange, 405, "这个地址不支持 " + method + " 请求");
        }));

        server.createContext("/api/users", safe(exchange -> {
            User me = lib.getUserByToken(readToken(exchange));
            if (me == null) {
                sendError(exchange, 401, "未登录或登录已过期");
                return;
            }
            // 这一段下面所有操作都要管理员，所以一次性挡住，不用每个分支重复
            if (!me.isAdmin()) {
                sendError(exchange, 403, "只有管理员能进行用户管理");
                return;
            }

            String method = exchange.getRequestMethod();
            String tail = exchange.getRequestURI().getPath().substring("/api/users".length());
            // tail 是 "" 或 "/zhang3/role" 或 "/zhang3/password"

            // ---------- GET /api/users：用户列表 ----------
            if ("GET".equals(method) && tail.isEmpty()) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (User u : lib.allUsers()) {
                    out.add(publicUser(u));      // ← 一个都不能漏！
                }
                send(exchange, 200, "application/json; charset=utf-8", GSON.toJson(out));
                return;
            }

            String[] seg = tail.split("/");
            // "/zhang3/role".split("/") -> ["", "zhang3", "role"]，长度 3
            if ("PUT".equals(method) && seg.length == 3) {
                String username = seg[1];
                String action = seg[2];

                // ---------- PUT /api/users/{username}/role ----------
                if ("role".equals(action)) {
                    RoleRequest req = GSON.fromJson(readBody(exchange), RoleRequest.class);
                    if (me.getUsername().equals(username) && !User.ROLE_ADMIN.equals(req.role)) {
                        sendError(exchange, 400, "不能取消自己的管理员身份");
                        return;
                    }
                    if (req == null || req.role == null
                            || (!User.ROLE_ADMIN.equals(req.role) && !User.ROLE_READER.equals(req.role))) {
                        sendError(exchange, 400, "请求体必须是 {\"role\":\"ADMIN\"} 或 {\"role\":\"READER\"}");
                        return;
                    }
                    send(exchange, 200, "application/json; charset=utf-8",
                            GSON.toJson(Map.of("message", lib.setRole(me, username, req.role))));
                    return;
                }

                // ---------- PUT /api/users/{username}/password ----------
                if ("password".equals(action)) {
                    PasswordRequest req = GSON.fromJson(readBody(exchange), PasswordRequest.class);
                    if (req == null || req.password == null || req.password.isEmpty()) {
                        sendError(exchange, 400, "请求体必须是 {\"password\":\"...\"}");
                        return;
                    }
                    send(exchange, 200, "application/json; charset=utf-8",
                            GSON.toJson(Map.of("message", lib.resetPassword(me, username, req.password))));
                    return;
                }
            }

            sendError(exchange, 405, "这个地址不支持 " + method + " 请求");
        }));

        server.createContext("/api/register", safe(exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "请用 POST 请求注册");
                return;
            }
            RegisterRequest req = GSON.fromJson(readBody(exchange), RegisterRequest.class);
            if (req == null || req.username == null || req.password == null || req.name == null) {
                sendError(exchange, 400, "请求体必须是 {\"username\":...,\"password\":...,\"name\":...}");
                return;
            }
            // 这几句跟 LibraryService.register 里是同一套规矩。
            // 在这再写一遍，是为了能回一个准确的 400/409（业务层那句仍然是最终防线）。
            if (!req.username.matches("[A-Za-z0-9]+")) {
                sendError(exchange, 400, "用户名只能由英文字母和数字组成。");
                return;
            }
            if (!req.name.matches("[\\p{IsHan}A-Za-z]+")) {
                sendError(exchange, 400, "姓名只能是汉字或英文字母。");
                return;
            }
            if (req.password.length() < 6) {
                sendError(exchange, 400, "密码至少 6 位。");
                return;
            }
            if (lib.findUser(req.username) != null) {
                sendError(exchange, 409, "用户名已被占用：" + req.username);
                return;
            }
            String regMsg = lib.register(req.username, req.password, req.name);
            send(exchange, 200, "application/json; charset=utf-8",
                    GSON.toJson(Map.of("message", regMsg)));
        }));

        server.start();
        System.out.println("服务器已启动：");
        System.out.println("  GET  http://localhost:8080");
        System.out.println("  POST http://localhost:8080/api/login   {\"username\":\"admin\",\"password\":\"admin123\"}");
        System.out.println("  GET  http://localhost:8080/api/me");
        System.out.println("  GET  http://localhost:8080/api/books");
        System.out.println("  GET  http://localhost:8080/api/borrows");
    }

    // ==================== 工具方法 ====================

    /** 从请求里把票掏出来：先看请求头 Authorization，再看地址栏 ?token= */
    private static String readToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();   // "Bearer " 是 7 个字符
        }
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("token=")) {
                    return pair.substring(6);
                }
            }
        }
        return null;
    }

    /** 把请求体整段读成字符串 */
    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** 一次响应的三步：设头 → 报状态和长度 → 写内容 */
    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 统一的失败回复：{"message":"..."} */
    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", GSON.toJson(Map.of("message", message)));
    }

    /** 对外的用户形状：只有账号、姓名、角色，绝不带密码 */
    private static Map<String, Object> publicUser(User u) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();   // 记住放入顺序，JSON 读起来更整齐
        map.put("username", u.getUsername());
        map.put("name", u.getName());
        map.put("role", u.getRole());
        return map;
    }

    /** 把任何 handler 包一层：出错时回一句 JSON，而不是掐断连接 */
    private static HttpHandler safe(HttpHandler handler) {
        return exchange -> {
            try {
                handler.handle(exchange);
            } catch (JsonSyntaxException e) {
                sendError(exchange, 400, "请求体不是合法的 JSON");
            } catch (Exception e) {
                sendError(exchange, 500, "服务器内部错误：" + e.getMessage());
            }
        };
    }
}
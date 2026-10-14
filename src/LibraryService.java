import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public class LibraryService {
    private List<Book> books = new ArrayList<>();   // 书籍列表
    private List<BorrowRecord> records = new ArrayList<>(); // 借阅记录列表
    private List<User> users = new ArrayList<>();   // 用户列表
    private static final Path FILE        = Paths.get("books.json");
    private static final Path RECORD_FILE = Paths.get("borrows.json");
    private static final Path USER_FILE   = Paths.get("users.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final int BORROW_DAYS = BorrowRecord.DEFAULT_DAYS;   // 没指定就借这么多天
    private static final long DAY_MS = BorrowRecord.DAY_MS;
    private static final int MAX_BORROW_DAYS = 365; // 最多借365天
    private static final String SALT = "libms::";
    private final Map<String, User> sessions = new HashMap<>();

    // 将明文密码通过 SHA-256 算法加密
    private static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("找不到 SHA-256 算法", e);
        }
    }

    // 用户名：只能英文 + 数字
    private static boolean isUserNameOk(String username) {
        return username.matches("[A-Za-z0-9]+");
    }

    // 姓名：只能汉字或字母
    private static boolean isNameOk(String name) {
        return name.matches("[\\p{IsHan}A-Za-z]+");
    }

    // 某个人自己的借阅记录（不看角色，就看他借的）—— 网页上「我的借阅」用这个
    public synchronized List<BorrowRecord> recordsOf(String username) {
        List<BorrowRecord> mine = new ArrayList<>();
        if (username == null) {
            return mine;                      // 没登录 -> 空列表，不抛异常
        }
        for (BorrowRecord r : records) {
            if (username.equals(r.getBorrower())) {
                mine.add(r);
            }
        }
        return mine;
    }

    // 把借阅表交给外面（给接口/网页用，不拼字）
    public synchronized List<BorrowRecord> allRecords() {
        return new ArrayList<>(records);
    }

    // 把整个书库交给外面（给接口/网页用，不拼字）
    public synchronized List<Book> allBooks() {
        return new ArrayList<>(books);
    }

    // 把用户表交给外面（给接口/网页用，不拼字）
    public synchronized List<User> allUsers() {
        return new ArrayList<>(users);
    }

    public synchronized String addBook(User operator, Book book) {
        if (operator == null || !operator.isAdmin()) {
            return "只有管理员能添加书籍。";
        }
        for (Book isBook : books) {
            if (isBook.getBookId().equals(book.getBookId())) {
                return "书籍ID已存在";
            }
        }
        books.add(book);
        save();
        return "添加书籍成功！";
    }

    public synchronized String deleteBook(User operator, String bookId) {
        Book target = findBook(bookId);
        if (operator == null || !operator.isAdmin()) {
            return "只有管理员能删除书籍。";
        }
        if (target == null) {
            return "没有这本书: " + bookId;
        }
        int lent = target.getTotal() - target.getAvailable();   // 在外借的册数：算出来的，不存
        if (lent > 0) {
            return "《" + target.getBookName() + "》还有 " + lent + " 册在外借，不能删除。";
        }
        books.remove(target);
        save();
        return "已删除：《" + target.getBookName() + "》";
    }

    public synchronized String updateBook(User operator, Book book) {
        Book target = findBook(book.getBookId());
        if (operator == null || !operator.isAdmin()) {
            return "只有管理员能修改书籍。";
        }
        if (target == null) {
            return "没有这本书: " + book.getBookId();
        }
        target.setBookName(book.getBookName());
        target.setAuthor(book.getAuthor());
        save();
        return "已修改：《" + book.getBookName() + "》";
    }

    // 查询书籍
    public synchronized String queryBooks(String keyword) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Book book : books) {
            boolean hit = book.getBookName().toLowerCase().contains(keyword.toLowerCase())
                    || book.getAuthor().toLowerCase().contains(keyword.toLowerCase());
            if (hit) {
                count++;
                sb.append(count).append(".").append(book).append("\n");
            }
        }
        if (count == 0) {
            return "未找到包含| " + keyword + " |的书籍\n";
        }
        return "共找到" + count + "本书:\n" + sb;
    }

    // 打印所有书籍列表
    public synchronized void printAll() {
        if (books.isEmpty()) {
            System.out.println("书架是空的");
            return;
        }
        for (int i = 0; i < books.size(); i++) {
            System.out.println((i + 1) + ". " + books.get(i));
        }
    }

    // 原子写
    private static void writeAtomic(Path target, String text, String errPrefix) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.out.println(errPrefix + e.getMessage());
        }
    }

    // 保存书籍列表
    public synchronized void save() {
        writeAtomic(FILE, GSON.toJson(books), "保存书籍失败: ");
    }

    // 加载书籍列表
    public synchronized void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            Book[] arr = GSON.fromJson(text, Book[].class);
            books = (arr == null) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(arr));
        } catch (IOException | JsonSyntaxException e) {
            System.out.println("读取书籍失败: " + e.getMessage());
        }
    }

    // 遍历找书
    public synchronized Book findBook(String bookId) {
        for (Book book : books) {
            if (bookId.equals(book.getBookId())) {
                return book;
            }
        }
        return null;
    }

    // 保存借阅记录
    public synchronized void saveBorrows() {
        writeAtomic(RECORD_FILE, GSON.toJson(records), "保存借阅记录失败: ");
    }

    // 加载借阅记录
    public synchronized void loadBorrows() {
        if (!Files.exists(RECORD_FILE)) {
            return;
        }
        try {
            String text = Files.readString(RECORD_FILE, StandardCharsets.UTF_8);
            BorrowRecord[] arr = GSON.fromJson(text, BorrowRecord[].class);
            records = (arr == null) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(arr));
        } catch (IOException | JsonSyntaxException e) {
            System.out.println("读取借阅记录失败: " + e.getMessage());
        }
    }

    // 借书（不指定天数 —— 老调用走这条路，按默认 30 天）
    public synchronized String borrowBook(User operator, String bookId) {
        return borrowBook(operator, bookId, BORROW_DAYS);      // ← 就一行，转发
    }

    // 借书
    public synchronized String borrowBook(User operator, String bookId, int days) {
        if (operator == null) { return "请先登录。"; }
        Book target = findBook(bookId);
        if (target == null) { return "没有这本书: " + bookId; }
        if (!target.canBorrow()) {
            return "《" + target.getBookName() + "》已借完，无法借出。";
        }
        if (days <= 0) {                                        // ← 新增：没填/填错 -> 30
            days = BORROW_DAYS;
        }
        if (days > MAX_BORROW_DAYS) {                           // ← 新增：夹到 365
            days = MAX_BORROW_DAYS;
        }
        target.borrowOne();
        BorrowRecord rec = new BorrowRecord(target.getBookId(), target.getBookName(),
                operator.getUsername(), days);                  // ← 4 参构造，把天数带进去
        records.add(rec);
        saveBorrows();
        save();
        return "借出成功: 《" + target.getBookName() + "》，借期 " + days + " 天，应还 "
                + rec.getDueTimeText() + "，剩余可借 " + target.getAvailable() + " 册";
    }

    // 还书
    public synchronized String returnBook(User operator, String bookId){
        if (operator == null) {
            return "请先登录。";
        }
        Book target = findBook(bookId);
        if (target == null) {
            return "没有这本书: " + bookId;
        }
        String borrower = operator.getUsername();
        BorrowRecord found = null;  // 创建查找器
        for (BorrowRecord r : records) {
            if (r.getBookId().equals(bookId)
                    && r.getBorrower().equals(borrower)
                    && !r.isReturned()) {
                found = r;
                break;
            }
        }
        if (found == null) {
            return "没有找到 " + borrower + " 借阅《" + target.getBookName() + "》的记录。";
        }
        found.setReturnTime(System.currentTimeMillis());
        target.returnOne();
        saveBorrows();
        save();
        return "归还成功: 《" + target.getBookName() + "》 归还人 " + borrower
                + "，剩余可借 " + target.getAvailable() + " 册";
    }

    // 打印借阅记录
    public synchronized void printRecords() {
        if (records.isEmpty()) {
            System.out.println("还没有任何借阅记录");
            return;
        }
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord r = records.get(i);
            String status;
            if (r.isReturned()) {
                status = "已归还 " + r.getReturnTimeText();
            } else {
                long due = r.getDueTime();           // ← 改：每条记录算自己的到期日
                long overdue = r.getOverdueDays();   // ← 改：同上
                if (overdue > 0) {
                    status = "在借中 —— 已逾期 " + overdue + " 天（应还 " + r.getDueTimeText()
                            + "，借期 " + r.getDays() + " 天）";
                } else {
                    status = "在借中，应还 " + r.getDueTimeText()
                            + "（借期 " + r.getDays() + " 天）";
                }
            }
            System.out.println((i + 1) + ". 《" + r.getBookName() + "》  借书人:" + r.getBorrower()
                    + "  借出:" + r.getBorrowTimeText() + "  " + status);
        }
    }

    // 保存用户
    public synchronized void saveUsers() {
        writeAtomic(USER_FILE, GSON.toJson(users), "保存用户失败: ");
    }

    public synchronized void loadUsers() {
        if (!Files.exists(USER_FILE)) {
            return;
        }
        try {
            String text = Files.readString(USER_FILE, StandardCharsets.UTF_8);
            User[] arr = GSON.fromJson(text, User[].class);
            users = (arr == null) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(arr));
        } catch (IOException | JsonSyntaxException e) {
            System.out.println("读取用户失败: " + e.getMessage());
        }
    }

    // 创建默认管理员
    public synchronized void initDefaultAdmin() {
        if (!users.isEmpty()) {
            return;
        }
        users.add(new User("admin", hash("admin123"), "管理员", User.ROLE_ADMIN));
        saveUsers();
        System.out.println("已创建默认管理员：admin / admin123（请尽快修改密码）");
    }

    // 遍历寻找用户
    public synchronized User findUser(String username) {
        for (User user : users) {
            if (username.equals(user.getUsername())) {
                return user;
            }
        }
        return null;
    }

    // 登录成功返回这个用户，失败返回 null
    public synchronized String login(String username, String password) {
        User user = findUser(username);
        if (user == null || !user.getPassword().equals(hash(password))) {
            return null;
        }
        String token = UUID.randomUUID().toString(); // 生成一段UID
        sessions.put(token, user);
        return token;
    }

    // 通过toke获取用户信息
    public synchronized User getUserByToken(String token) {
        if (token == null) {
            return null;
        }
        return sessions.get(token);
    }

    // 退出登录：token作废
    public synchronized void logout(String token) {
        sessions.remove(token);
    }

    // 注册：新来的人一律是读者
    public synchronized String register(String username, String password, String name) {
        if (!isUserNameOk(username)) {
            return "用户名只能由英文字母和数字组成。";
        }
        if (!isNameOk(name)) {
            return "姓名只能是汉字或英文字母。";
        }
        if (password.length() < 6) {
            return "密码至少 6 位。";
        }
        if (findUser(username) != null) {
            return "用户名已被占用：" + username;
        }
        users.add(new User(username, hash(password), name));    // ← 3 参，自动 ROLE_READER
        saveUsers();
        return "注册成功，请返回登录：" + username;
    }

    // 列出所有用户
    public synchronized String listUsers(User operator) {
        if (operator == null || !operator.isAdmin()) {
            return "只有管理员能查看用户列表。";
        }
        StringBuilder sb = new StringBuilder();
        for (User u : users) {
            sb.append(u.getUsername()).append("  ").append(u.getName())
                    .append("  ").append(u.isAdmin() ? "管理员" : "读者")
                    .append(u.getUsername().equals(operator.getUsername()) ? "  ← 我" : "")
                    .append("\n");
        }
        return "共 " + users.size() + " 个账号：\n" + sb;
    }

    // 设为管理员 / 取消管理员（换成 User.ROLE_ADMIN 或 User.ROLE_READER）
    public synchronized String setRole(User operator, String username, String role) {
        if (operator == null || !operator.isAdmin()) {
            return "只有管理员能修改用户角色。";
        }
        User target = findUser(username);
        if (target == null) {
            return "没有这个用户: " + username;
        }
        if (target.isAdmin() && User.ROLE_READER.equals(role)) {
            int adminCount = 0;
            for (User u : users) {
                if (u.isAdmin()) {
                    adminCount++;
                }
            }
            if (adminCount <= 1) {
                return "这是最后一个管理员，不能取消。";
            }
        }
        target.setRole(role);
        saveUsers();
        return "已把 " + username + " 设为"
                + (User.ROLE_ADMIN.equals(role) ? "管理员" : "读者");
    }

    // 重置密码
    public synchronized String resetPassword(User operator, String username, String newPassword) {
        if (operator == null || !operator.isAdmin()) {
            return "只有管理员能重置密码。";
        }
        if (newPassword.length() < 6) {
            return "密码至少 6 位。";
        }
        User target = findUser(username);
        if (target == null) {
            return "没有这个用户: " + username;
        }
        target.setPassword(hash(newPassword));
        saveUsers();
        return "已重置 " + username + " 的密码。";
    }

    // 初始化数据库
    public synchronized void seedIfEmpty() {
        if (!books.isEmpty()) {
            return;
        }
        books.add(new Book("B001", "Java核心技术", "Horstmann", 3, 3));
        books.add(new Book("B002", "算法导论", "Cormen", 2, 2));
        books.add(new Book("B003", "深入理解计算机系统", "Bryant", 2, 2));
        books.add(new Book("B004", "代码大全", "McConnell", 1, 1));
        books.add(new Book("B005", "重构", "Fowler", 2, 2));
        books.add(new Book("B006", "设计模式", "Gamma", 1, 1));
        books.add(new Book("B007", "沉默的大多数", "王小波", 2, 2));
        books.add(new Book("B008", "活着", "余华", 1, 1));
        books.add(new Book("B009", "百年孤独", "马尔克斯", 1, 1));
        books.add(new Book("B010", "人类简史", "赫拉利", 3, 3));
        save();
        System.out.println("已灌入 " + books.size() + " 本演示书。");
    }
}

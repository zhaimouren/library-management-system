import java.util.Scanner;

public class Main {
    private static final String MENU_ADMIN =
            "1.添加  2.删除  3.修改  4.查询  5.显示全部  6.借书  7.还书  8.查看记录  9.用户管理  0.退出";
    private static final String MENU_READER =
            "4.查询  5.显示全部  6.借书  7.还书  8.查看记录  0.退出";

    private static void userMenu(Scanner sc, LibraryService lib, User me) {
        while (true) {
            System.out.println("--- 用户管理 ---");
            System.out.println("1.查看所有用户  2.设为管理员  3.取消管理员  4.重置密码  0.返回");
            System.out.print("请选择：");
            String c = sc.nextLine().trim();

            if ("0".equals(c)) {
                return;
            } else if ("1".equals(c)) {
                System.out.println(lib.listUsers(me));
            } else if ("2".equals(c)) {
                System.out.print("用户名：");
                System.out.println(lib.setRole(me, sc.nextLine().trim(), User.ROLE_ADMIN));
            } else if ("3".equals(c)) {
                System.out.print("用户名：");
                System.out.println(lib.setRole(me, sc.nextLine().trim(), User.ROLE_READER));
            } else if ("4".equals(c)) {
                System.out.print("用户名：");
                String u = sc.nextLine().trim();
                System.out.print("新密码（至少 6 位）：");
                System.out.println(lib.resetPassword(me, u, sc.nextLine()));
            } else {
                System.out.println("没有这个选项");
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        LibraryService lib = new LibraryService();
        lib.load();
        lib.seedIfEmpty();
        lib.loadBorrows();
        lib.loadUsers();
        lib.initDefaultAdmin();

        // —— 登录 ——
        String token = null;
        User me = null;
        while (me == null) {
            System.out.println("1.登录  2.注册账号");
            System.out.print("请选择：");
            String action = sc.nextLine().trim();

            if ("2".equals(action)) {
                System.out.print("用户名：");
                String username = sc.nextLine().trim();
                System.out.print("姓名：");
                String name = sc.nextLine().trim();
                System.out.print("密码（至少 6 位）：");
                String password = sc.nextLine();
                System.out.println(lib.register(username, password, name));
                continue;
            }

            System.out.print("用户名: ");
            String username = sc.nextLine().trim();
            System.out.print("密码: ");
            String password = sc.nextLine();

            token = lib.login(username, password);
            if (token == null) {
                System.out.println("用户名或密码错误，请重试。");
            } else {
                me = lib.getUserByToken(token);
            }
        }
        System.out.println("欢迎来到终末地，" + me.getName() + "！");

        while (true) {
            System.out.println(me.isAdmin() ? MENU_ADMIN : MENU_READER);
            System.out.print("请选择：");

            String choice = sc.nextLine().trim();

            if ("0".equals(choice)) {
                lib.logout(token);
                System.out.println("再见");
                break;
            } else if ("1".equals(choice)) {
                System.out.print("书籍ID：");
                String id = sc.nextLine().trim();
                System.out.print("书名：");
                String name = sc.nextLine().trim();
                System.out.print("作者：");
                String author = sc.nextLine().trim();
                System.out.println(lib.addBook(me, new Book(id, name, author)));
            } else if ("2".equals(choice)) {
                System.out.print("输入想要删除的书籍ID: ");
                String id = sc.nextLine().trim();
                System.out.println(lib.deleteBook(me, id));
            } else if ("3".equals(choice)) {
                System.out.print("输入想要修改的书籍ID: ");
                String id = sc.nextLine().trim();
                System.out.print("输入书籍的名称: ");
                String name = sc.nextLine().trim();
                System.out.print("输入书籍的作者: ");
                String author = sc.nextLine().trim();
                System.out.println(lib.updateBook(me, new Book(id, name, author)));
            } else if ("4".equals(choice)) {
                System.out.print("输入想要查询的书籍名称: ");
                String name = sc.nextLine().trim();
                System.out.print(lib.queryBooks(name));
            } else if ("5".equals(choice)) {
                lib.printAll();
            } else if ("6".equals(choice)) {
                System.out.print("输入想要借阅的书籍ID: ");
                String id = sc.nextLine().trim();
                System.out.print("借多少天（直接回车 = " + BorrowRecord.DEFAULT_DAYS + " 天）: ");
                int day;
                try {
                    day = Integer.parseInt(sc.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("（不是数字，按默认 " + BorrowRecord.DEFAULT_DAYS + " 天算）");
                    day = BorrowRecord.DEFAULT_DAYS;
                }
                System.out.println(lib.borrowBook(me, id, day));
            } else if ("7".equals(choice)) {
                System.out.print("输入想要归还的书籍ID: ");
                String id = sc.nextLine().trim();
                System.out.println(lib.returnBook(me, id));
            } else if ("8".equals(choice)) {
                lib.printRecords();
            } else if ("9".equals(choice)) {
                if (me.isAdmin()) {
                    userMenu(sc, lib, me);
                } else {
                    System.out.println("没有这个选项");
                }
            } else {
                System.out.println("没有这个选项");
            }
        }
    }
}

public class BorrowRecord {
    private String bookId;        // 哪本书
    private String bookName;      // 书名（冗余存一份，见下面说明）
    private String borrower;      // 借书人姓名（先手工输入，第 7 步再接真正的用户）
    private long borrowTime;      // 借出时间，存成毫秒数（System.currentTimeMillis()）
    private long returnTime;      // 归还时间；0 表示【还没还】—— 这是关键设计
    private int days;             // 借期天数；0 表示"这条老数据没存过天数"，按默认值算

    public static final int  DEFAULT_DAYS = 30;                   // 没特别指定就借 30 天
    public static final long DAY_MS = 24L * 60 * 60 * 1000;       // 一天的毫秒数

    public boolean isReturned() {
        return returnTime > 0;
    }

    // 借期天数：老记录里没有 days 这个字段，Gson 读回来是 0 —— 用默认值兜底
    public int getDays() {
        return days > 0 ? days : DEFAULT_DAYS;
    }

    public void setDays(int days) {
        this.days = days;
    }

    // 应该哪天还 = 借出时间 + 借期天数
    public long getDueTime() {
        return borrowTime + (long) getDays() * DAY_MS;
    }

    // 逾期了几天；没逾期是 0 或负数，调用方自己判断
    public long getOverdueDays() {
        if (isReturned()) {
            return 0;
        }
        return (System.currentTimeMillis() - getDueTime()) / DAY_MS;
    }

    @Override
    public String toString() {
        return "《" + bookName + "》 " + borrower + " " + getBorrowTimeText() + " 借出";
    }

    public BorrowRecord() {
    }

    // 借书时用：刚借出，还没还 —— 没指定天数，走默认
    public BorrowRecord(String bookId, String bookName, String borrower) {
        this(bookId, bookName, borrower, DEFAULT_DAYS);
    }

    // 借书时用：指定借多少天
    public BorrowRecord(String bookId, String bookName, String borrower, int days) {
        this.bookId = bookId;
        this.bookName = bookName;
        this.borrower = borrower;
        this.days = days > 0 ? days : DEFAULT_DAYS;
        this.borrowTime = System.currentTimeMillis();
        this.returnTime = 0;
    }

    // 存盘读回时用：时间和归还状态都由文件决定
    public BorrowRecord(String bookId, String bookName, String borrower, long borrowTime, long returnTime) {
        this.bookId = bookId;
        this.bookName = bookName;
        this.borrower = borrower;
        this.borrowTime = borrowTime;
        this.returnTime = returnTime;
    }

    private static final java.time.format.DateTimeFormatter FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 更改毫秒时间戳
    public static String timeText(long millis) {
        return FMT.withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(millis));
    }

    // 返回借书时间文本
    public String getBorrowTimeText() {
        return timeText(borrowTime);
    }

    // 返回还书时间文本
    public String getReturnTimeText() {
        return returnTime == 0 ? "未归还" : timeText(returnTime);
    }

    // 返回应还时间文本
    public String getDueTimeText() {
        return timeText(getDueTime());
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
    }

    public long getBorrowTime() {
        return borrowTime;
    }

    public void setBorrowTime(long borrowTime) {
        this.borrowTime = borrowTime;
    }

    public long getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(long returnTime) {
        this.returnTime = returnTime;
    }
}
public class Book {
    private String bookId;
    private String bookName;
    private String author;
    private int total = 1;
    private int available = 1;

    public String getBookId() {
        return bookId;
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public Book() {
    }

    public Book(String bookId, String bookName, String author) {
        this(bookId, bookName, author, 1, 1);        // ← 委托给下面那个，默认 1 册
    }

    public Book(String bookId, String bookName, String author, int total, int available) {
        this.bookId = bookId;
        this.bookName = bookName;
        this.author = author;
        this.total = total;
        this.available = available;
    }


    public boolean canBorrow(){
        return available > 0;
    }

    public boolean canReturn() {
        return available < total;        // 还有册在外面，才允许还
    }

    public void borrowOne(){
        available--;
    }

    public void returnOne(){
        available++;
    }

    @Override
    public String toString() {
        return bookId + " | " + bookName + " | " + author;
    }
}

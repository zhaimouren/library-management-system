public class User {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_READER = "READER";

    private String username;
    private String password;
    private String name;
    private String role;

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }

    @Override
    public String toString() {
        return username + "(" + name + ", " + (isAdmin() ? "管理员" : "读者") + ")";
    }

    public User() {
    }

    public User(String username, String password, String name) {
        this(username, password, name, ROLE_READER);   // 默认读者
    }

    public User(String username, String password, String name, String role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
    }
}

package at.leoway.api.security;

/** The two roles LeoWay knows (S02). The admin role only reaches health and feed endpoints. */
public final class Roles {
    public static final String STUDENT = "student";
    public static final String ADMIN = "admin";

    private Roles() {}
}

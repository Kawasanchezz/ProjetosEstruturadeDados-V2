package com.treebom.security;

/** Quem está chamando. */
public record Principal(long userId, String username, String role) {
    public static final String VIEWER = "viewer";
    public static final String ADMIN = "admin";

    public static int rank(String papel) {
        return switch (papel) {
            case VIEWER -> 1;
            case ADMIN -> 2;
            default -> 0;
        };
    }

    public boolean atende(String papelExigido) {
        return rank(role) >= rank(papelExigido);
    }
}

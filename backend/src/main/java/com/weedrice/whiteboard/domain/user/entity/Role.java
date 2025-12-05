package com.weedrice.whiteboard.domain.user.entity;

public final class Role {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String BOARD_ADMIN = "BOARD_ADMIN";
    public static final String USER = "USER";

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_SUPER_ADMIN = ROLE_PREFIX + SUPER_ADMIN;
    public static final String ROLE_BOARD_ADMIN = ROLE_PREFIX + BOARD_ADMIN;
    public static final String ROLE_USER = ROLE_PREFIX + USER;

    private Role() {}
}

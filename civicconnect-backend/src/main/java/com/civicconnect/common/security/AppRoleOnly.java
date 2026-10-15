package com.civicconnect.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method that must run as the application's own login role (civicconnect_app), not
 * the logged-in user's role: login, registration, /auth/me and the per-request account check. These read
 * account rows before anyone is logged in, or read columns the user's role is not allowed to read.
 * See DbSessionInterceptor.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AppRoleOnly {}

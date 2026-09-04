package com.nguyenvu.lopet.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as requiring a signed-in caller. Without it the endpoint is public,
 * and still sees the viewer through {@link CurrentUser} when a valid token is sent.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
}

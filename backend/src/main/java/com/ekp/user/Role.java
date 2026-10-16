package com.ekp.user;

/**
 * Coarse-grained authorization roles. Kept deliberately small for v1
 * (ADMIN, USER); finer-grained permissions can layer on later without
 * changing the token contract.
 */
public enum Role {
    ADMIN,
    USER
}

package com.flightlog.auth;

import java.time.Instant;

/** users 表的一行。 */
public record UserAccount(
    long id, String email, String passwordHash, String displayName, Instant createdAt) {}

package com.flightlog.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private static final String COLUMNS = "id, email, password_hash, display_name, created_at";

  private final JdbcClient jdbc;

  public UserRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UserAccount> findByEmail(String email) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = :email")
        .param("email", email)
        .query(UserRepository::map)
        .optional();
  }

  public Optional<UserAccount> findById(long id) {
    return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id")
        .param("id", id)
        .query(UserRepository::map)
        .optional();
  }

  public boolean existsByEmail(String email) {
    return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :email")
            .param("email", email)
            .query(Integer.class)
            .single()
        > 0;
  }

  public UserAccount insert(String email, String passwordHash, String displayName, Instant now) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO users (email, password_hash, display_name, created_at)
            VALUES (:email, :passwordHash, :displayName, :createdAt)
            """)
        .param("email", email)
        .param("passwordHash", passwordHash)
        .param("displayName", displayName)
        .param("createdAt", now.toString())
        .update(keys);
    long id = keys.getKey().longValue();
    return new UserAccount(id, email, passwordHash, displayName, now);
  }

  public void updatePasswordHash(long id, String passwordHash) {
    jdbc.sql("UPDATE users SET password_hash = :passwordHash WHERE id = :id")
        .param("passwordHash", passwordHash)
        .param("id", id)
        .update();
  }

  private static UserAccount map(ResultSet rs, int rowNum) throws SQLException {
    return new UserAccount(
        rs.getLong("id"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getString("display_name"),
        Instant.parse(rs.getString("created_at")));
  }
}

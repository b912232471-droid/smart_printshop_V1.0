package com.example.scheduleservice.repository;

import com.example.scheduleservice.model.JwAccount;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JwAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public JwAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<JwAccount> findByUserId(long userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                            SELECT id, user_id, student_id, jw_password
                            FROM jw_accounts
                            WHERE user_id = ?
                            """,
                    (rs, rowNum) -> new JwAccount(
                            rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("student_id"),
                            rs.getString("jw_password")
                    ),
                    userId
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void saveOrUpdate(long userId, String studentId, String encryptedPassword) {
        if (findByUserId(userId).isPresent()) {
            if (hasJwUsernameColumn()) {
                jdbcTemplate.update(
                        """
                                UPDATE jw_accounts
                                SET student_id = ?, jw_username = ?, jw_password = ?, updated_at = CURRENT_TIMESTAMP
                                WHERE user_id = ?
                                """,
                        studentId,
                        studentId,
                        encryptedPassword,
                        userId
                );
            } else {
                jdbcTemplate.update(
                        """
                                UPDATE jw_accounts
                                SET student_id = ?, jw_password = ?, updated_at = CURRENT_TIMESTAMP
                                WHERE user_id = ?
                                """,
                        studentId,
                        encryptedPassword,
                        userId
                );
            }
            return;
        }
        if (hasJwUsernameColumn()) {
            jdbcTemplate.update(
                    """
                            INSERT INTO jw_accounts(user_id, student_id, jw_username, jw_password)
                            VALUES (?, ?, ?, ?)
                            """,
                    userId,
                    studentId,
                    studentId,
                    encryptedPassword
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO jw_accounts(user_id, student_id, jw_password)
                        VALUES (?, ?, ?)
                        """,
                userId,
                studentId,
                encryptedPassword
        );
    }

    private boolean hasJwUsernameColumn() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'jw_accounts'
                          AND COLUMN_NAME = 'jw_username'
                        """,
                Integer.class
        );
        return count != null && count > 0;
    }
}

package com.example.scheduleservice.repository;

import com.example.scheduleservice.model.CourseSchedule;
import com.example.scheduleservice.model.Term;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CourseScheduleRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CourseSchedule> findByUserAndTerm(long userId, String xnm, String xqm) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, user_id, student_id, xnm, xqm, kcmc, xqj, jcs, cdmc, xm
                FROM course_schedules
                WHERE user_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (xnm != null && !xnm.isBlank()) {
            sql.append(" AND xnm = ?");
            args.add(xnm.trim());
        }
        if (xqm != null && !xqm.isBlank()) {
            sql.append(" AND xqm = ?");
            args.add(xqm.trim());
        }
        sql.append(" ORDER BY xnm DESC, xqm DESC, xqj, jcs, id");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new CourseSchedule(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("student_id"),
                rs.getString("xnm"),
                rs.getString("xqm"),
                rs.getString("kcmc"),
                rs.getString("xqj"),
                rs.getString("jcs"),
                rs.getString("cdmc"),
                rs.getString("xm")
        ), args.toArray());
    }

    public List<Term> terms(long userId) {
        return jdbcTemplate.query(
                """
                        SELECT DISTINCT xnm, xqm
                        FROM course_schedules
                        WHERE user_id = ?
                        ORDER BY xnm DESC, xqm DESC
                        """,
                (rs, rowNum) -> new Term(rs.getString("xnm"), rs.getString("xqm")),
                userId
        );
    }

    public void replaceTerm(long userId, String studentId, String xnm, String xqm, List<CourseSchedule> courses) {
        jdbcTemplate.update(
                "DELETE FROM course_schedules WHERE user_id = ? AND xnm = ? AND xqm = ?",
                userId,
                xnm,
                xqm
        );
        for (CourseSchedule course : courses) {
            jdbcTemplate.update(
                    """
                            INSERT INTO course_schedules(user_id, student_id, xnm, xqm, kcmc, xqj, jcs, cdmc, xm)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    userId,
                    studentId,
                    course.xnm(),
                    course.xqm(),
                    course.kcmc(),
                    course.xqj(),
                    course.jcs(),
                    course.cdmc(),
                    course.xm()
            );
        }
    }
}

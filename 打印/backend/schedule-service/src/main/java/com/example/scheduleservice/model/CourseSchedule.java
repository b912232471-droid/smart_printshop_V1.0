package com.example.scheduleservice.model;

public record CourseSchedule(
        long id,
        long userId,
        String studentId,
        String xnm,
        String xqm,
        String kcmc,
        String xqj,
        String jcs,
        String cdmc,
        String xm
) {
}

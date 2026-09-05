package com.example.scheduleservice.dto;

import com.example.scheduleservice.model.CourseSchedule;

public record CourseScheduleResponse(
        String studentId,
        String xnm,
        String xqm,
        String kcmc,
        String xqj,
        String jcs,
        String cdmc,
        String xm
) {
    public static CourseScheduleResponse from(CourseSchedule course) {
        return new CourseScheduleResponse(
                course.studentId(),
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

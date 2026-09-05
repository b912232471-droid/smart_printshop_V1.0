package com.example.scheduleservice.service.sync;

import com.example.scheduleservice.model.CourseSchedule;
import com.example.scheduleservice.model.JwAccount;

import java.util.List;

public interface ScheduleSyncAdapter {
    boolean isConfigured();

    List<CourseSchedule> fetchCourses(long userId, JwAccount account, String jwPassword, String xnm, String xqm);
}

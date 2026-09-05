package com.example.scheduleservice.service.sync;

import com.example.scheduleservice.common.ApiException;
import com.example.scheduleservice.model.CourseSchedule;
import com.example.scheduleservice.model.JwAccount;

import java.util.List;

public class RoutingScheduleSyncAdapter implements ScheduleSyncAdapter {
    private final List<ScheduleSyncAdapter> candidates;

    public RoutingScheduleSyncAdapter(List<ScheduleSyncAdapter> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    @Override
    public boolean isConfigured() {
        return candidates.stream().anyMatch(ScheduleSyncAdapter::isConfigured);
    }

    @Override
    public List<CourseSchedule> fetchCourses(long userId, JwAccount account, String jwPassword, String xnm, String xqm) {
        return candidates.stream()
                .filter(ScheduleSyncAdapter::isConfigured)
                .findFirst()
                .orElseThrow(() -> ApiException.serviceUnavailable("教务同步适配器未配置"))
                .fetchCourses(userId, account, jwPassword, xnm, xqm);
    }
}

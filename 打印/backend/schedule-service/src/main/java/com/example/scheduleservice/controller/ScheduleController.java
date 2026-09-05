package com.example.scheduleservice.controller;

import com.example.scheduleservice.dto.BindRequest;
import com.example.scheduleservice.dto.BindStatusResponse;
import com.example.scheduleservice.dto.CourseScheduleResponse;
import com.example.scheduleservice.dto.SyncRequest;
import com.example.scheduleservice.dto.SyncResponse;
import com.example.scheduleservice.dto.TermResponse;
import com.example.scheduleservice.security.AuthContext;
import com.example.scheduleservice.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/bind")
    public BindStatusResponse bind(@Valid @RequestBody BindRequest request) {
        return scheduleService.bind(AuthContext.requireUserId(), request);
    }

    @GetMapping("/bind-status")
    public BindStatusResponse bindStatus() {
        return scheduleService.bindStatus(AuthContext.requireUserId());
    }

    @PostMapping("/sync")
    public SyncResponse sync(@Valid @RequestBody(required = false) SyncRequest request) {
        return scheduleService.sync(AuthContext.requireUserId(), request);
    }

    @GetMapping
    public List<CourseScheduleResponse> schedules(@RequestParam(required = false) String xnm,
                                                  @RequestParam(required = false) String xqm) {
        return scheduleService.schedules(AuthContext.requireUserId(), xnm, xqm);
    }

    @GetMapping("/terms")
    public List<TermResponse> terms() {
        return scheduleService.terms(AuthContext.requireUserId());
    }
}

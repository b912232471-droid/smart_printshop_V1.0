package com.example.scheduleservice.service;

import com.example.scheduleservice.common.ApiException;
import com.example.scheduleservice.dto.BindRequest;
import com.example.scheduleservice.dto.BindStatusResponse;
import com.example.scheduleservice.dto.CourseScheduleResponse;
import com.example.scheduleservice.dto.SyncRequest;
import com.example.scheduleservice.dto.SyncResponse;
import com.example.scheduleservice.dto.TermResponse;
import com.example.scheduleservice.model.CourseSchedule;
import com.example.scheduleservice.model.JwAccount;
import com.example.scheduleservice.repository.CourseScheduleRepository;
import com.example.scheduleservice.repository.JwAccountRepository;
import com.example.scheduleservice.security.FieldCryptoService;
import com.example.scheduleservice.service.sync.ScheduleSyncAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.List;

@Service
public class ScheduleService {
    private final JwAccountRepository accountRepository;
    private final CourseScheduleRepository courseRepository;
    private final FieldCryptoService cryptoService;
    private final ScheduleSyncAdapter syncAdapter;
    private final boolean mockSyncEnabled;

    public ScheduleService(JwAccountRepository accountRepository,
                           CourseScheduleRepository courseRepository,
                           FieldCryptoService cryptoService,
                           ScheduleSyncAdapter syncAdapter,
                           @Value("${schedule.sync.mock-enabled:false}") boolean mockSyncEnabled) {
        this.accountRepository = accountRepository;
        this.courseRepository = courseRepository;
        this.cryptoService = cryptoService;
        this.syncAdapter = syncAdapter;
        this.mockSyncEnabled = mockSyncEnabled;
    }

    public BindStatusResponse bind(long userId, BindRequest request) {
        String encryptedPassword = cryptoService.encrypt(request.jwPassword());
        accountRepository.saveOrUpdate(
                userId,
                request.studentId().trim(),
                encryptedPassword
        );
        return bindStatus(userId);
    }

    public BindStatusResponse bindStatus(long userId) {
        return accountRepository.findByUserId(userId)
                .map(account -> new BindStatusResponse(true, account.studentId()))
                .orElseGet(() -> new BindStatusResponse(false, null));
    }

    public SyncResponse sync(long userId, SyncRequest request) {
        JwAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("请先绑定教务账号"));
        String xnm = blankToDefault(request == null ? null : request.xnm(), defaultSchoolYear());
        String xqm = blankToDefault(request == null ? null : request.xqm(), "1");
        if (syncAdapter.isConfigured()) {
            String jwPassword = cryptoService.decrypt(account.jwPassword());
            List<CourseSchedule> courses = syncAdapter.fetchCourses(userId, account, jwPassword, xnm, xqm);
            courseRepository.replaceTerm(userId, account.studentId(), xnm, xqm, courses);
            return new SyncResponse(courses.size(), xnm, xqm);
        }
        if (!mockSyncEnabled) {
            throw ApiException.serviceUnavailable("教务同步适配器未配置");
        }
        List<CourseSchedule> courses = List.of(new CourseSchedule(
                0,
                userId,
                account.studentId(),
                xnm,
                xqm,
                "示例课程",
                "1",
                "1-2",
                "教学楼101",
                "授课教师"
        ));
        courseRepository.replaceTerm(userId, account.studentId(), xnm, xqm, courses);
        return new SyncResponse(courses.size(), xnm, xqm);
    }

    public List<CourseScheduleResponse> schedules(long userId, String xnm, String xqm) {
        return courseRepository.findByUserAndTerm(userId, xnm, xqm).stream()
                .map(CourseScheduleResponse::from)
                .toList();
    }

    public List<TermResponse> terms(long userId) {
        return courseRepository.terms(userId).stream()
                .map(TermResponse::from)
                .toList();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String defaultSchoolYear() {
        int year = Year.now().getValue();
        return (year - 1) + "-" + year;
    }
}

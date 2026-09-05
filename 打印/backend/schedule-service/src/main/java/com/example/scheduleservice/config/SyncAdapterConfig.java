package com.example.scheduleservice.config;

import com.example.scheduleservice.service.sync.HttpJsonScheduleSyncAdapter;
import com.example.scheduleservice.service.sync.RoutingScheduleSyncAdapter;
import com.example.scheduleservice.service.sync.ScheduleSyncAdapter;
import com.example.scheduleservice.service.sync.ZfsoftScheduleSyncAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class SyncAdapterConfig {
    @Bean
    @Primary
    public ScheduleSyncAdapter routingScheduleSyncAdapter(
            ZfsoftScheduleSyncAdapter zfsoftScheduleSyncAdapter,
            HttpJsonScheduleSyncAdapter httpJsonScheduleSyncAdapter) {
        return new RoutingScheduleSyncAdapter(List.of(zfsoftScheduleSyncAdapter, httpJsonScheduleSyncAdapter));
    }
}

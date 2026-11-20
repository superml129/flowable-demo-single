package com.sheldon.flowable_demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Data
@Component
@ConfigurationProperties(prefix = "flow.remind")
public class RemindProperties {
    /** 催办延迟时长。yaml 里可写 30s / 15m / 24h，Spring 自动换算 */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration delay = Duration.ofSeconds(30);   // 兜底默认值
}
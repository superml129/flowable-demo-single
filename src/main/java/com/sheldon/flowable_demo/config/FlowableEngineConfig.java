package com.sheldon.flowable_demo.config;

import com.sheldon.flowable_demo.listener.TaskCompleteNotifyEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Flowable 引擎配置。
 * 把全局事件监听器（任务完成 -> 通知流程发起人）注册到引擎，
 * 所有流程定义自动生效，BPMN 里无需逐节点配置监听器。
 */
@Configuration
public class FlowableEngineConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> taskCompleteNotifyConfigurer(
            TaskCompleteNotifyEventListener listener) {
        return engineConfiguration ->
                engineConfiguration.setEventListeners(Collections.singletonList(listener));
    }
}

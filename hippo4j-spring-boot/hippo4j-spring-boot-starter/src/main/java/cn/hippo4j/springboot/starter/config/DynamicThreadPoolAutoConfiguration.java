/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.springboot.starter.config;

import cn.hippo4j.adapter.base.ThreadPoolAdapterBeanContainer;
import cn.hippo4j.adapter.web.WebThreadPoolHandlerChoose;
import cn.hippo4j.common.api.ThreadDetailState;
import cn.hippo4j.common.api.ThreadPoolCheckAlarm;
import cn.hippo4j.common.api.ThreadPoolConfigChange;
import cn.hippo4j.common.api.ThreadPoolDynamicRefresh;
import cn.hippo4j.common.config.ApplicationContextHolder;
import cn.hippo4j.core.config.UtilAutoConfiguration;
import cn.hippo4j.core.enable.MarkerConfiguration;
import cn.hippo4j.core.executor.state.ThreadPoolRunStateHandler;
import cn.hippo4j.core.executor.support.service.DynamicThreadPoolService;
import cn.hippo4j.core.handler.DynamicThreadPoolBannerHandler;
import cn.hippo4j.core.toolkit.IdentifyUtil;
import cn.hippo4j.core.toolkit.inet.InetUtils;
import cn.hippo4j.message.api.NotifyConfigBuilder;
import cn.hippo4j.message.config.MessageConfiguration;
import cn.hippo4j.message.service.AlarmControlHandler;
import cn.hippo4j.message.service.DefaultThreadPoolCheckAlarmHandler;
import cn.hippo4j.message.service.DefaultThreadPoolConfigChangeHandler;
import cn.hippo4j.message.service.Hippo4jBaseSendMessageService;
import cn.hippo4j.message.service.Hippo4jSendMessageService;
import cn.hippo4j.springboot.starter.adapter.web.WebAdapterConfiguration;
import cn.hippo4j.springboot.starter.controller.ThreadPoolAdapterController;
import cn.hippo4j.springboot.starter.controller.WebThreadPoolController;
import cn.hippo4j.springboot.starter.controller.WebThreadPoolRunStateController;
import cn.hippo4j.springboot.starter.core.BaseThreadDetailStateHandler;
import cn.hippo4j.springboot.starter.core.ClientWorker;
import cn.hippo4j.springboot.starter.core.DynamicThreadPoolSubscribeConfig;
import cn.hippo4j.springboot.starter.core.ServerThreadPoolDynamicRefresh;
import cn.hippo4j.springboot.starter.core.ThreadPoolAdapterRegister;
import cn.hippo4j.springboot.starter.event.ApplicationContentPostProcessor;
import cn.hippo4j.springboot.starter.monitor.ReportingEventExecutor;
import cn.hippo4j.springboot.starter.monitor.collect.RunTimeInfoCollector;
import cn.hippo4j.springboot.starter.monitor.send.MessageSender;
import cn.hippo4j.springboot.starter.monitor.send.http.HttpConnectSender;
import cn.hippo4j.springboot.starter.notify.ServerNotifyConfigBuilder;
import cn.hippo4j.springboot.starter.remote.ClientShutDownService;
import cn.hippo4j.springboot.starter.remote.HttpAgent;
import cn.hippo4j.springboot.starter.remote.HttpScheduledHealthCheck;
import cn.hippo4j.springboot.starter.remote.ServerHealthCheck;
import cn.hippo4j.springboot.starter.remote.ServerHttpAgent;
import cn.hippo4j.springboot.starter.support.DynamicThreadPoolConfigService;
import cn.hippo4j.springboot.starter.support.DynamicThreadPoolPostProcessor;
import cn.hippo4j.springboot.starter.support.ThreadPoolPluginRegisterPostProcessor;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Dynamic thread-pool auto-configuration.
 */
@Configuration
@AllArgsConstructor
@ConditionalOnBean(MarkerConfiguration.Marker.class)
@EnableConfigurationProperties(BootstrapProperties.class)
@ConditionalOnProperty(prefix = BootstrapProperties.PREFIX, value = "enable", matchIfMissing = true, havingValue = "true")
@ImportAutoConfiguration({WebAdapterConfiguration.class, NettyClientConfiguration.class, DiscoveryConfiguration.class, MessageConfiguration.class, UtilAutoConfiguration.class})
public class DynamicThreadPoolAutoConfiguration {

    private final BootstrapProperties properties;

    private final ConfigurableEnvironment environment;

    @Bean
    public DynamicThreadPoolBannerHandler threadPoolBannerHandler() {
        return new DynamicThreadPoolBannerHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationContextHolder hippo4JApplicationContextHolder() {
        return new ApplicationContextHolder();
    }

    @Bean
    public ClientWorker hippo4jClientWorker(HttpAgent httpAgent,
                                            InetUtils hippo4JInetUtils,
                                            ServerHealthCheck serverHealthCheck, ClientShutDownService clientShutDownService) {
        String identify = IdentifyUtil.generate(environment, hippo4JInetUtils);
        return new ClientWorker(httpAgent, identify, serverHealthCheck, clientShutDownService);
    }

    @Bean
    @SuppressWarnings("all")
    public DynamicThreadPoolService dynamicThreadPoolConfigService(HttpAgent httpAgent,
                                                                   ServerHealthCheck serverHealthCheck,
                                                                   ServerNotifyConfigBuilder notifyConfigBuilder,
                                                                   Hippo4jBaseSendMessageService hippo4jBaseSendMessageService,
                                                                   DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig) {
        return new DynamicThreadPoolConfigService(httpAgent, properties, notifyConfigBuilder, hippo4jBaseSendMessageService, dynamicThreadPoolSubscribeConfig);
    }

    @Bean
    @SuppressWarnings("all")
    public DynamicThreadPoolPostProcessor threadPoolBeanPostProcessor(HttpAgent httpAgent,
                                                                      ApplicationContextHolder hippo4JApplicationContextHolder,
                                                                      DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig) {
        return new DynamicThreadPoolPostProcessor(properties, httpAgent, dynamicThreadPoolSubscribeConfig);
    }

    @Bean
    @ConditionalOnMissingBean(value = ThreadDetailState.class)
    public ThreadDetailState baseThreadDetailStateHandler() {
        return new BaseThreadDetailStateHandler();
    }

    @Bean
    public WebThreadPoolRunStateController poolRunStateController(ThreadPoolRunStateHandler threadPoolRunStateHandler,
                                                                  ThreadDetailState threadDetailState) {
        return new WebThreadPoolRunStateController(threadPoolRunStateHandler, threadDetailState);
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("all")
    public MessageSender messageSender(HttpAgent httpAgent) {
        return new HttpConnectSender(httpAgent);
    }

    @Bean
    public ReportingEventExecutor reportingEventExecutor(BootstrapProperties properties,
                                                         MessageSender messageSender,
                                                         ServerHealthCheck serverHealthCheck) {
        return new ReportingEventExecutor(properties, messageSender, serverHealthCheck);
    }

    @Bean
    @SuppressWarnings("all")
    public ServerHealthCheck httpScheduledHealthCheck(HttpAgent httpAgent) {
        return new HttpScheduledHealthCheck(httpAgent);
    }

    @Bean
    public ClientShutDownService clientShutDownService() {
        return new ClientShutDownService();
    }

    @Bean
    public RunTimeInfoCollector runTimeInfoCollector() {
        return new RunTimeInfoCollector(properties);
    }

    @Bean
    @SuppressWarnings("all")
    public ThreadPoolAdapterController threadPoolAdapterController(InetUtils hippo4JInetUtils) {
        return new ThreadPoolAdapterController(environment, hippo4JInetUtils);
    }

    @Bean
    public ThreadPoolAdapterBeanContainer threadPoolAdapterBeanContainer() {
        return new ThreadPoolAdapterBeanContainer();
    }

    @Bean
    public ApplicationContentPostProcessor applicationContentPostProcessor() {
        return new ApplicationContentPostProcessor();
    }

    @Bean
    @SuppressWarnings("all")
    public WebThreadPoolController webThreadPoolController(WebThreadPoolHandlerChoose webThreadPoolServiceChoose) {
        return new WebThreadPoolController(webThreadPoolServiceChoose);
    }

    @Bean
    @SuppressWarnings("all")
    public ThreadPoolAdapterRegister threadPoolAdapterRegister(HttpAgent httpAgent,
                                                               InetUtils hippo4JInetUtils) {
        return new ThreadPoolAdapterRegister(httpAgent, properties, environment, hippo4JInetUtils);
    }

    @Bean
    public NotifyConfigBuilder serverNotifyConfigBuilder(HttpAgent httpAgent,
                                                         BootstrapProperties properties,
                                                         AlarmControlHandler alarmControlHandler) {
        return new ServerNotifyConfigBuilder(httpAgent, properties, alarmControlHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolCheckAlarm defaultThreadPoolCheckAlarmHandler(Hippo4jSendMessageService hippo4jSendMessageService) {
        return new DefaultThreadPoolCheckAlarmHandler(hippo4jSendMessageService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolConfigChange defaultThreadPoolConfigChangeHandler(Hippo4jSendMessageService hippo4jSendMessageService) {
        return new DefaultThreadPoolConfigChangeHandler(hippo4jSendMessageService);
    }

    @Bean
    public ThreadPoolDynamicRefresh threadPoolDynamicRefresh(ThreadPoolConfigChange threadPoolConfigChange) {
        return new ServerThreadPoolDynamicRefresh(threadPoolConfigChange);
    }

    @Bean
    public DynamicThreadPoolSubscribeConfig dynamicThreadPoolSubscribeConfig(ThreadPoolDynamicRefresh threadPoolDynamicRefresh,
                                                                             ClientWorker clientWorker) {
        return new DynamicThreadPoolSubscribeConfig(threadPoolDynamicRefresh, clientWorker, properties);
    }

    @Bean
    public HttpAgent httpAgent(BootstrapProperties properties) {
        return new ServerHttpAgent(properties);
    }

    @Bean
    public ThreadPoolPluginRegisterPostProcessor threadPoolPluginRegisterPostProcessor() {
        return new ThreadPoolPluginRegisterPostProcessor();
    }

}

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

package cn.hippo4j.springboot.starter.core;

import static cn.hippo4j.common.constant.Constants.BASE_PATH;

import cn.hippo4j.common.api.ClientCloseHookExecute;
import cn.hippo4j.common.config.ApplicationContextHolder;
import cn.hippo4j.common.constant.Constants;
import cn.hippo4j.common.design.builder.ThreadFactoryBuilder;
import cn.hippo4j.common.model.InstanceInfo;
import cn.hippo4j.common.web.base.Result;
import cn.hippo4j.common.web.base.Results;
import cn.hippo4j.common.web.exception.ErrorCodeEnum;
import cn.hippo4j.springboot.starter.remote.HttpAgent;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

/**
 * Discovery client.
 */
@Slf4j
public class DiscoveryClient implements DisposableBean {

    private final ScheduledExecutorService scheduler;

    private final HttpAgent httpAgent;

    private final InstanceInfo instanceInfo;

    private volatile long lastSuccessfulHeartbeatTimestamp = -1;

    private static final String PREFIX = "DiscoveryClient_";

    private final String appPathIdentifier;

    public DiscoveryClient(HttpAgent httpAgent, InstanceInfo instanceInfo) {
        this.httpAgent = httpAgent;
        this.instanceInfo = instanceInfo;
        this.appPathIdentifier = instanceInfo.getAppName().toUpperCase() + "/" + instanceInfo.getInstanceId();
        this.scheduler = new ScheduledThreadPoolExecutor(
                new Integer(1),
                ThreadFactoryBuilder.builder().daemon(true).prefix("client.discovery.scheduler").build());
        register();
        // Init the schedule tasks.
        initScheduledTasks();
    }

    private void initScheduledTasks() {
        scheduler.scheduleWithFixedDelay(new HeartbeatThread(), 30, 30, TimeUnit.SECONDS);
    }

    boolean register() {
        log.info("{}{} - registering service...", PREFIX, appPathIdentifier);
        String urlPath = BASE_PATH + "/apps/register/";
        Result registerResult;
        try {
            registerResult = httpAgent.httpPostByDiscovery(urlPath, instanceInfo);
        } catch (Exception ex) {
            registerResult = Results.failure(ErrorCodeEnum.SERVICE_ERROR);
            log.error("{}{} - registration failed: {}", PREFIX, appPathIdentifier, ex.getMessage());
        }
        if (log.isInfoEnabled()) {
            log.info("{}{} - registration status: {}", PREFIX, appPathIdentifier, registerResult.isSuccess() ? "success" : "fail");
        }
        return registerResult.isSuccess();
    }

    @Override
    public void destroy() throws Exception {
        log.info("{}{} - destroy service...", PREFIX, appPathIdentifier);
        String clientCloseUrlPath = Constants.BASE_PATH + "/client/close";
        Result clientCloseResult;
        try {
            this.scheduler.shutdown();
            boolean b = this.scheduler.awaitTermination(3, TimeUnit.SECONDS);
            log.info("renew | 关闭线程调度器 | {}", b);
            String groupKeyIp = new StringBuilder()
                    .append(instanceInfo.getGroupKey())
                    .append(Constants.GROUP_KEY_DELIMITER)
                    .append(instanceInfo.getIdentify())
                    .toString();
            ClientCloseHookExecute.ClientCloseHookReq clientCloseHookReq = new ClientCloseHookExecute.ClientCloseHookReq();
            clientCloseHookReq.setAppName(instanceInfo.getAppName())
                    .setInstanceId(instanceInfo.getInstanceId())
                    .setGroupKey(groupKeyIp);
            clientCloseResult = httpAgent.httpPostByDiscovery(clientCloseUrlPath, clientCloseHookReq);
            if (clientCloseResult.isSuccess()) {
                log.info("{}{} -client close hook success.", PREFIX, appPathIdentifier);
            }
        } catch (Throwable ex) {
            if (ex instanceof ShutdownExecuteException) {
                return;
            }
            log.error("{}{} - client close hook fail.", PREFIX, appPathIdentifier, ex);
        }
    }

    public class HeartbeatThread implements Runnable {

        @Override
        public void run() {
            if (renew()) {
                lastSuccessfulHeartbeatTimestamp = System.currentTimeMillis();
            }
        }
    }

    private boolean renew() {
        Result renewResult;
        try {
            if (this.scheduler.isShutdown()) {
                log.info("renew | 定时调用线程已关闭");
                return false;
            }
            InstanceInfo.InstanceRenew instanceRenew = new InstanceInfo.InstanceRenew()
                    .setAppName(instanceInfo.getAppName())
                    .setInstanceId(instanceInfo.getInstanceId())
                    .setLastDirtyTimestamp(instanceInfo.getLastDirtyTimestamp().toString())
                    .setStatus(instanceInfo.getStatus().toString());
            renewResult = httpAgent.httpPostByDiscovery(BASE_PATH + "/apps/renew", instanceRenew);
            if (Objects.equals(ErrorCodeEnum.NOT_FOUND.getCode(), renewResult.getCode())) {
                long timestamp = instanceInfo.setIsDirtyWithTime();
                boolean success = register();
                // TODO Abstract server registration logic
                ThreadPoolAdapterRegister adapterRegister = ApplicationContextHolder.getBean(ThreadPoolAdapterRegister.class);
                adapterRegister.register();
                if (success) {
                    instanceInfo.unsetIsDirty(timestamp);
                }
                return success;
            }
            return renewResult.isSuccess();
        } catch (Exception ex) {
            log.error(PREFIX + "{} - was unable to send heartbeat!", appPathIdentifier, ex);
            return false;
        }
    }
}

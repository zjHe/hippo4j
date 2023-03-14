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

package cn.hippo4j.core.executor.support.adpter;

import cn.hippo4j.common.toolkit.ReflectUtil;
import cn.hippo4j.core.executor.DynamicThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Zipkin thread local executor adapter.
 */
public class ZipkinExecutorAdapter implements DynamicThreadPoolAdapter {

    private final static String MATCH_CLASS_NAME = "brave.internal.WrappingExecutorService";
    private final static String FIELD_NAME = "delegate";
    private final static String TYPE_NAME = "java.util.concurrent.ExecutorService";

    @Override
    public boolean match(Object executor) {
        return matchSuper(executor);
    }

    public boolean matchSuper(Object executor) {
        if (Objects.equals(MATCH_CLASS_NAME, executor.getClass().getName())) {
            return true;
        }
        // else {
        // return Objects.equals(MATCH_CLASS_NAME, executor.getClass().getSuperclass().getName());
        // }
        return false;
    }

    @Override
    public DynamicThreadPoolExecutor unwrap(Object executor) {
        Object unwrap = doUnwrap(executor);
        if (unwrap instanceof DynamicThreadPoolExecutor) {
            return (DynamicThreadPoolExecutor) unwrap;
        }
        if (executor instanceof ThreadPoolTaskExecutor) {
            return new ThreadPoolTaskExecutorAdapter().unwrap(executor);
        }
        return null;
    }

    @Override
    public void replace(Object executor, Executor dynamicThreadPoolExecutor) {
        Field field = ReflectUtil.findField(executor, FIELD_NAME, TYPE_NAME);
        ReflectUtil.setFieldValue(executor, field, dynamicThreadPoolExecutor);
    }

    private Object doUnwrap(Object executor) {
        Object unwrap = ReflectUtil.getFieldValue(executor, FIELD_NAME);
        if (unwrap == null) {
            Field field = ReflectUtil.findField(executor, FIELD_NAME, TYPE_NAME);
            if (field != null) {
                return ReflectUtil.getFieldValue(executor, field);
            }
        }
        return null;
    }
}

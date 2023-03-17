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

package cn.hippo4j.springboot.starter.remote;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientShutDownService {

    private volatile boolean clientShutDown;

    private CountDownLatch countDownLatch = new CountDownLatch(1);

    public ClientShutDownService() {
        this.clientShutDown = false;
    }

    public void await(long timeout) throws InterruptedException {
        setClientShutDown(true);
        countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
    }

    public boolean countDown() {
        if (isClientShutDown()) {
            countDownLatch.countDown();
            return true;
        }
        return false;
    }

    public boolean isClientShutDown() {
        return clientShutDown;
    }

    public void setClientShutDown(boolean clientShutDown) {
        this.clientShutDown = clientShutDown;
    }
}

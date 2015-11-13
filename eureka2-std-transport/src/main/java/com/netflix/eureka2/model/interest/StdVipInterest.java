/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.interest;

import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class StdVipInterest extends StdAbstractPatternInterest<InstanceInfo> {

    protected StdVipInterest() {
    }

    public StdVipInterest(String vip) {
        super(vip);
    }

    public StdVipInterest(String vip, Operator operator) {
        super(vip, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getVipAddress();
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Vip;
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }
}

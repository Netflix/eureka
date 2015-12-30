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

package com.netflix.eureka2.utils;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import org.junit.Test;

import static com.netflix.eureka2.utils.EurekaTextFormatters.toQuery;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class EurekaTextFormattersTest {

    @Test
    public void testSimpleQueryFormatting() throws Exception {
        Interest<InstanceInfo> appInterest = Interests.forApplications("testApp");
        String query = toQuery(appInterest);
        assertThat(query, is(equalTo("Interest{application=testApp}")));
    }

    @Test
    public void testNoneQueryFormatting() throws Exception {
        Interest<InstanceInfo> appInterest = Interests.forNone();
        String query = toQuery(appInterest);
        assertThat(query, is(equalTo("Interest{none}")));
    }

    @Test
    public void testCompositeQueryFormatting() throws Exception {
        Interest<InstanceInfo> compositeInterest = Interests.forSome(
                Interests.forApplications("testApp"),
                Interests.forVips(Interest.Operator.Like, "unsecureTestVip"),
                Interests.forSecureVips(Interest.Operator.Equals, "secureTestVip")
        );
        String query = toQuery(compositeInterest);
        assertThat(query, containsString("vip~=unsecureTestVip"));
        assertThat(query, containsString("application=testApp"));
        assertThat(query, containsString("secureVip=secureTestVip"));
    }
}
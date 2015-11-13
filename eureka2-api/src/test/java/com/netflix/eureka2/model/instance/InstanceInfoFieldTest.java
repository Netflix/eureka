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

package com.netflix.eureka2.model.instance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author David Liu
 */
public class InstanceInfoFieldTest {

    @Test(timeout = 60000)
    public void shouldHaveSameNumberOfFieldsAsInstanceInfoVariablesWithGetters() throws Exception {
        Set<String> expectedFields = getExpectedFields();

        // remove non-settable fields
        expectedFields.remove("id");
        expectedFields.remove("version");

        Field[] instanceInfoFields = InstanceInfoField.class.getFields();  // get only public ones
        Set<String> actualFields = new HashSet<String>();
        for (Field field : instanceInfoFields) {
            InstanceInfoField iif = (InstanceInfoField) field.get(null);
            String name = iif.getFieldName().name();
            actualFields.add(Character.toLowerCase(name.charAt(0)) + name.substring(1));
        }

        assertThat(actualFields.size(), equalTo(expectedFields.size()));
        assertThat(actualFields, containsInAnyOrder(expectedFields.toArray()));
    }

    private static Set<String> getExpectedFields() {
        Set<String> expectedFields = new HashSet<String>();
        for (Method method : InstanceInfo.class.getMethods()) {
            String name = method.getName();
            if (name.startsWith("get") && method.getParameterCount() == 0) {
                expectedFields.add(Character.toLowerCase(name.charAt(3)) + name.substring(4));
            }
        }
        return expectedFields;
    }
}

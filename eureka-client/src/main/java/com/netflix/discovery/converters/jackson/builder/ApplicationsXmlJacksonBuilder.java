/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.discovery.converters.jackson.builder;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.netflix.discovery.shared.Application;

/**
 * Add XML specific annotation to {@link ApplicationsJacksonBuilder}.
 */
public class ApplicationsXmlJacksonBuilder extends ApplicationsJacksonBuilder {

    @Override
    @JacksonXmlElementWrapper(useWrapping = false)
    public void withApplication(List<Application> applications) {
        super.withApplication(applications);
    }
}

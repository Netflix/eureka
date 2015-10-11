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

package com.netflix.discovery.shared.dns;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Tomasz Bak
 */
public interface DnsService {

    /**
     * Resolve host name to the bottom A-Record or the latest available CNAME
     *
     * @return IP address
     */
    String resolveIp(String hostName);

    /**
     * Resolve A-record entry for a given domain name.
     */
    @Nullable
    List<String> resolveARecord(String rootDomainName);
}
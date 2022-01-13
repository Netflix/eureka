/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.discovery;

import com.google.inject.ImplementedBy;
import com.netflix.discovery.shared.Applications;

/**
 * A simple contract for <em>eureka</em> clients to fallback for getting
 * registry information in case eureka clients are unable to retrieve this
 * information from any of the <em>eureka</em> servers.
 *
 * <p>
 * This is normally not required, but for applications that cannot exist without
 * the registry information it can provide some additional reslience.
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
@ImplementedBy(NotImplementedRegistryImpl.class)
public interface BackupRegistry {

    Applications fetchRegistry();

    Applications fetchRegistry(String[] includeRemoteRegions);
}

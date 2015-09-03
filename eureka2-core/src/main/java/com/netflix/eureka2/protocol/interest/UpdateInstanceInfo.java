/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.protocol.interest;

import com.netflix.eureka2.protocol.common.InterestSetNotification;
import com.netflix.eureka2.model.instance.Delta;

/**
 * @author Tomasz Bak
 */
public class UpdateInstanceInfo implements InterestSetNotification {

    private final DeltaDTO<?> deltaDTO;

    // For serialization framework
    protected UpdateInstanceInfo() {
        deltaDTO = null;
    }

    public UpdateInstanceInfo(Delta<?> delta) {
        deltaDTO = DeltaDTO.toDeltaDTO(delta);
    }

    public Delta<?> getDelta() {
        return deltaDTO.toDelta();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UpdateInstanceInfo that = (UpdateInstanceInfo) o;

        if (deltaDTO != null ? !deltaDTO.equals(that.deltaDTO) : that.deltaDTO != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return deltaDTO != null ? deltaDTO.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UpdateInstanceInfo{deltaDTO=" + deltaDTO + '}';
    }
}

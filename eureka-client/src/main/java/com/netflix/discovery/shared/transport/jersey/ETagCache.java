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

package com.netflix.discovery.shared.transport.jersey;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.decorator.EurekaHttpClientDecorator.RequestType;

/**
 * Eureka sends ETag with each data reply. A client can use this information to avoid unneeded data transfer.
 * Using ETags make it possible to increase polling frequency without affecting the system performance
 * significantly.
 */
public class ETagCache {

    private final ConcurrentMap<ETagCacheKey, ETagCacheValue> cache = new ConcurrentHashMap<>();

    public void cacheReply(String etag, RequestType requestType, String[] regions, Applications applications) {
        cache.put(new ETagCacheKey(requestType, regions), new ETagCacheValue(etag, applications));
    }

    public String getApplicationsETag(RequestType requestType, String[] regions) {
        ETagCacheValue cacheValue = cache.get(new ETagCacheKey(requestType, regions));
        return cacheValue == null ? null : cacheValue.getEtag();
    }

    public Applications getCachedApplications(RequestType requestType, String[] regions) {
        ETagCacheValue cacheValue = cache.get(new ETagCacheKey(requestType, regions));
        return cacheValue == null ? null : cacheValue.getApplications();
    }

    static class ETagCacheKey {
        private final RequestType requestType;
        private final String[] regions;

        ETagCacheKey(RequestType requestType, String[] regions) {
            this.requestType = requestType;
            this.regions = regions;
        }

        public RequestType getRequestType() {
            return requestType;
        }

        public String[] getRegions() {
            return regions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            ETagCacheKey that = (ETagCacheKey) o;

            if (requestType != that.requestType)
                return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            return Arrays.equals(regions, that.regions);

        }

        @Override
        public int hashCode() {
            int result = requestType != null ? requestType.hashCode() : 0;
            result = 31 * result + Arrays.hashCode(regions);
            return result;
        }
    }

    static class ETagCacheValue {
        private final String etag;
        private final Applications applications;

        ETagCacheValue(String etag, Applications applications) {
            this.etag = etag;
            this.applications = applications;
        }

        public String getEtag() {
            return etag;
        }

        public Applications getApplications() {
            return applications;
        }
    }
}

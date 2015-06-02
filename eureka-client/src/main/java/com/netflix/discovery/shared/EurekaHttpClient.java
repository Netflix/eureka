package com.netflix.discovery.shared;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;

/**
 * Low level Eureka HTTP client API.
 *
 * @author Tomasz Bak
 */
public interface EurekaHttpClient {

    HttpResponse<Void> register(InstanceInfo info);

    HttpResponse<Void> cancel(String appName, String id);

    HttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus);

    HttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info);

    HttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info);

    HttpResponse<InstanceInfo> getInstance(String appName, String id);

    void shutdown();

    class HttpResponse<T> {
        private final int statusCode;
        private final T entity;

        public HttpResponse(int statusCode) {
            this(statusCode, null);
        }

        public HttpResponse(int statusCode, T entity) {
            this.statusCode = statusCode;
            this.entity = entity;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public T getEntity() {
            return entity;
        }

        public static <T> HttpResponse<T> responseWith(int status) {
            return new HttpResponse<>(status, null);
        }

        public static <T> HttpResponse<T> responseWith(int status, T entity) {
            return new HttpResponse<>(status, entity);
        }
    }

}

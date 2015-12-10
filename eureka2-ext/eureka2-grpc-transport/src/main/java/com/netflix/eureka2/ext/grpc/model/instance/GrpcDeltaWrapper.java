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

package com.netflix.eureka2.ext.grpc.model.instance;

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 */
public class GrpcDeltaWrapper<ValueType> implements GrpcObjectWrapper<Eureka2.GrpcDelta>, Delta<ValueType> {

    private final Eureka2.GrpcDelta grpcDelta;

    public GrpcDeltaWrapper(Eureka2.GrpcDelta grpcDelta) {
        this.grpcDelta = grpcDelta;
    }

    @Override
    public String getId() {
        return grpcDelta.getId();
    }

    @Override
    public InstanceInfoField<ValueType> getField() {
        Eureka2.GrpcDelta.GrpcDeltaValue.OneofDeltaCase deltaCase = grpcDelta.getDeltaValue().getOneofDeltaCase();
        InstanceInfoField<?> field;
        switch (deltaCase) {
            case APP:
                field = InstanceInfoField.APPLICATION;
                break;
            case APPGROUP:
                field = InstanceInfoField.APPLICATION_GROUP;
                break;
            case ASG:
                field = InstanceInfoField.ASG;
                break;
            case DATACENTERINFO:
                field = InstanceInfoField.DATA_CENTER_INFO;
                break;
            case HEALTHCHECKURLS:
                field = InstanceInfoField.HEALTHCHECK_URLS;
                break;
            case HOMEPAGEURL:
                field = InstanceInfoField.HOMEPAGE_URL;
                break;
            case METADATA:
                field = InstanceInfoField.META_DATA;
                break;
            case PORTS:
                field = InstanceInfoField.PORTS;
                break;
            case SECUREVIPADDRESS:
                field = InstanceInfoField.SECURE_VIP_ADDRESS;
                break;
            case STATUS:
                field = InstanceInfoField.STATUS;
                break;
            case STATUSPAGEURL:
                field = InstanceInfoField.STATUS_PAGE_URL;
                break;
            case VIPADDRESS:
                field = InstanceInfoField.VIP_ADDRESS;
                break;
            default:
                throw new IllegalStateException("Unrecognized delta type " + deltaCase);
        }
        return (InstanceInfoField<ValueType>) field;
    }

    @Override
    public ValueType getValue() {
        Eureka2.GrpcDelta.GrpcDeltaValue.OneofDeltaCase deltaCase = grpcDelta.getDeltaValue().getOneofDeltaCase();
        Object value;
        switch (deltaCase) {
            case APP:
                value = getGrpcObject().getDeltaValue().getApp();
                break;
            case APPGROUP:
                value = getGrpcObject().getDeltaValue().getAppGroup();
                break;
            case ASG:
                value = getGrpcObject().getDeltaValue().getAsg();
                break;
            case DATACENTERINFO:
                value = getGrpcObject().getDeltaValue().getDataCenterInfo();
                break;
            case HEALTHCHECKURLS:
                value = getGrpcObject().getDeltaValue().getHealthCheckUrls();
                break;
            case HOMEPAGEURL:
                value = getGrpcObject().getDeltaValue().getHomePageUrl();
                break;
            case METADATA:
                value = getGrpcObject().getDeltaValue().getMetaData();
                break;
            case PORTS:
                value = getGrpcObject().getDeltaValue().getPorts();
                break;
            case SECUREVIPADDRESS:
                value = getGrpcObject().getDeltaValue().getSecureVipAddress();
                break;
            case STATUS:
                value = GrpcInstanceInfoWrapper.toStatus(getGrpcObject().getDeltaValue().getStatus());
                break;
            case STATUSPAGEURL:
                value = getGrpcObject().getDeltaValue().getStatusPageUrl();
                break;
            case VIPADDRESS:
                value = getGrpcObject().getDeltaValue().getVipAddress();
                break;
            default:
                throw new IllegalStateException("Unrecognized delta type " + deltaCase);
        }
        return (ValueType) value;
    }

    @Override
    public InstanceInfoBuilder applyTo(InstanceInfoBuilder instanceInfoBuilder) {
        return getField().update(instanceInfoBuilder, getValue());
    }

    @Override
    public Eureka2.GrpcDelta getGrpcObject() {
        return grpcDelta;
    }

    @Override
    public int hashCode() {
        return grpcDelta.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GrpcDeltaWrapper) {
            return grpcDelta.equals(((GrpcDeltaWrapper) obj).getGrpcObject());
        }
        return false;
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcDelta).replace("\n", " ");
    }

    public static final class Builder extends DeltaBuilder {
        @SuppressWarnings("unchecked")
        public GrpcDeltaWrapper<?> build() {
            if (id == null || field == null) {  // null data.value is ok
                throw new IllegalStateException("Incomplete delta information");
            }
            Eureka2.GrpcDelta grpcDelta = Eureka2.GrpcDelta.newBuilder()
                    .setId(id)
                    .setDeltaValue(toGrpcDeltaValue(field, value))
                    .build();

            return new GrpcDeltaWrapper<>(grpcDelta);
        }

        private Eureka2.GrpcDelta.GrpcDeltaValue toGrpcDeltaValue(InstanceInfoField<?> field, Object value) {
            Eureka2.GrpcDelta.GrpcDeltaValue.Builder builder = Eureka2.GrpcDelta.GrpcDeltaValue.newBuilder();
            Eureka2.GrpcDelta.GrpcDeltaValue grpcValue;
            switch (field.getFieldName()) {
                case App:
                    grpcValue = builder.setApp((String) value).build();
                    break;
                case AppGroup:
                    grpcValue = builder.setAppGroup((String) value).build();
                    break;
                case Asg:
                    grpcValue = builder.setAsg((String) value).build();
                    break;
                case DataCenterInfo:
                    grpcValue = builder.setDataCenterInfo(((GrpcObjectWrapper<Eureka2.GrpcDataCenterInfo>) value).getGrpcObject()).build();
                    break;
                case HealthCheckUrls:
                    Eureka2.GrpcDelta.StringSet grpcHealthUrls = Eureka2.GrpcDelta.StringSet.newBuilder().addAllValues((Iterable<String>) value).build();
                    grpcValue = builder.setHealthCheckUrls(grpcHealthUrls).build();
                    break;
                case HomePageUrl:
                    grpcValue = builder.setHomePageUrl((String) value).build();
                    break;
                case MetaData:
                    Eureka2.GrpcDelta.GrpcMetaData grpcMetaData = Eureka2.GrpcDelta.GrpcMetaData.newBuilder().putAllMetaData((Map<String, String>) value).build();
                    grpcValue = builder.setMetaData(grpcMetaData).build();
                    break;
                case Ports:
                    Set<GrpcServicePortWrapper> ports = (Set<GrpcServicePortWrapper>) value;
                    List<Eureka2.GrpcServicePort> grpcPorts = new ArrayList<>(ports.size());
                    for (GrpcServicePortWrapper port : ports) {
                        grpcPorts.add(port.getGrpcObject());
                    }
                    Eureka2.GrpcDelta.GrpcServicePortSet grpcPortSet = Eureka2.GrpcDelta.GrpcServicePortSet.newBuilder().addAllPorts(grpcPorts).build();
                    grpcValue = builder.setPorts(grpcPortSet).build();
                    break;
                case SecureVipAddress:
                    grpcValue = builder.setSecureVipAddress((String) value).build();
                    break;
                case Status:
                    grpcValue = builder.setStatus(GrpcInstanceInfoWrapper.toGrpcStatus((InstanceInfo.Status) value)).build();
                    break;
                case StatusPageUrl:
                    grpcValue = builder.setStatusPageUrl((String) value).build();
                    break;
                case VipAddress:
                    grpcValue = builder.setVipAddress((String) value).build();
                    break;
                default:
                    throw new IllegalStateException("Unexpected delta type " + field.getFieldName());
            }
            return grpcValue;
        }
    }
}

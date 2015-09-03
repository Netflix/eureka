package com.netflix.eureka2.protocol.interest;

import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.model.instance.ServicePort;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * @author Tomasz Bak
 */
public abstract class DeltaDTO<T> {
    protected final String id;
    protected final String fieldName;

    protected DeltaDTO() {
        id = null;
        fieldName = null;
    }

    protected DeltaDTO(Delta<T> delta) {
        this.id = delta.getId();
        this.fieldName = delta.getField().getFieldName().name();
    }

    public abstract T getValue();

    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeltaDTO deltaDTO = (DeltaDTO) o;

        if (fieldName != null ? !fieldName.equals(deltaDTO.fieldName) : deltaDTO.fieldName != null) {
            return false;
        }
        if (id != null ? !id.equals(deltaDTO.id) : deltaDTO.id != null) {
            return false;
        }
        Object thisValue = getValue();
        Object thatValue = deltaDTO.getValue();
        if (thisValue != null ? !thisValue.equals(thatValue) : thatValue != null) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public Delta<T> toDelta() {
        return (Delta<T>) new Delta.Builder()
                .withId(id)
                .withDelta(InstanceInfoField.forName(InstanceInfoField.Name.forName(fieldName)), getValue())
                .build();
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (fieldName != null ? fieldName.hashCode() : 0);
        Object value = getValue();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DeltaDTO{" +
                "id='" + id + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", value=" + getValue() +
                '}';
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DeltaDTO<?> toDeltaDTO(Delta<?> delta) {
        Type type = delta.getField().getValueType();
        if (type instanceof Class) {
            Class<?> ctype = (Class<?>) type;
            if (ctype.equals(String.class)) {
                return new StringDeltaDTO((Delta<String>) delta);
            } else if (Enum.class.isAssignableFrom(ctype)) {
                return new EnumDeltaDTO((Delta<Enum>) delta);
            } else if (DataCenterInfo.class.isAssignableFrom(ctype)) {
                return new DataCenterInfoDTO((Delta<DataCenterInfo>) delta);
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType ptype = (ParameterizedType) type;
            if (Set.class.isAssignableFrom((Class<?>) ptype.getRawType())) {
                Type targ = ptype.getActualTypeArguments()[0];
                if (ServicePort.class.equals(targ)) {
                    return new SetServicePortDeltaDTO((Delta<Set<ServicePort>>) delta);
                } else if (String.class.equals(targ)) {
                    return new SetStringDeltaDTO((Delta<Set<String>>) delta);
                }
            } else if (Map.class.isAssignableFrom((Class<?>) ptype.getRawType())) {
                Type keyType = ptype.getActualTypeArguments()[0];
                Type valueType = ptype.getActualTypeArguments()[1];
                if (String.class.equals(keyType) && String.class.equals(valueType)) {
                    return new MapStringDeltaDTO((Delta<Map<String, String>>) delta);
                }
            }
        }
        throw new IllegalArgumentException("Unexpected delta type " + type);
    }

    //
    // Type specific implementations
    //

    public static class StringDeltaDTO extends DeltaDTO<String> {
        private String value;

        public StringDeltaDTO() {
        }

        public StringDeltaDTO(Delta<String> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    @SuppressWarnings("rawtypes")
    public static class EnumDeltaDTO extends DeltaDTO<Enum> {
        Enum value;

        public EnumDeltaDTO() {
        }

        public EnumDeltaDTO(Delta<Enum> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Enum getValue() {
            return value;
        }
    }

    public static class SetServicePortDeltaDTO extends DeltaDTO<Set<ServicePort>> {
        Set<ServicePort> value;

        public SetServicePortDeltaDTO() {
        }

        public SetServicePortDeltaDTO(Delta<Set<ServicePort>> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Set<ServicePort> getValue() {
            return value;
        }
    }

    public static class SetStringDeltaDTO extends DeltaDTO<Set<String>> {
        Set<String> value;

        public SetStringDeltaDTO() {
        }

        public SetStringDeltaDTO(Delta<Set<String>> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Set<String> getValue() {
            return value;
        }
    }

    public static class MapStringDeltaDTO extends DeltaDTO<Map<String, String>> {
        Map<String, String> value;

        public MapStringDeltaDTO() {
        }

        public MapStringDeltaDTO(Delta<Map<String, String>> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public Map<String, String> getValue() {
            return value;
        }
    }

    public static class DataCenterInfoDTO extends DeltaDTO<DataCenterInfo> {
        DataCenterInfo value;

        public DataCenterInfoDTO() {
        }

        public DataCenterInfoDTO(Delta<DataCenterInfo> delta) {
            super(delta);
            value = delta.getValue();
        }

        @Override
        public DataCenterInfo getValue() {
            return value;
        }
    }
}
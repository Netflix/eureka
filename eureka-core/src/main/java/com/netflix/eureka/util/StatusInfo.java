package com.netflix.eureka.util;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * An utility class for exposing status information of an instance.
 *
 * @author Greg Kim
 */
@Serializer("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("status")
public class StatusInfo {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z";

    public static final class Builder {

        @XStreamOmitField
        private StatusInfo result;

        private Builder() {
            result = new StatusInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder isHealthy(boolean b) {
            result.isHeathly = Boolean.valueOf(b);
            return this;
        }

        public Builder withInstanceInfo(InstanceInfo instanceInfo) {
            result.instanceInfo = instanceInfo;
            return this;
        }

        /**
         * Add any application specific status data.
         */
        public Builder add(String key, String value) {
            if (result.applicationStats == null) {
                result.applicationStats = new HashMap<String, String>();
            }
            result.applicationStats.put(key, value);
            return this;
        }

        /**
         * Build the {@link StatusInfo}. General information are automatically
         * built here too.
         */
        public StatusInfo build() {
            if (result.instanceInfo == null) {
                throw new IllegalStateException("instanceInfo can not be null");
            }

            result.generalStats.put("server-uptime", getUpTime());
            result.generalStats.put("environment", ConfigurationManager
                    .getDeploymentContext().getDeploymentEnvironment());

            Runtime runtime = Runtime.getRuntime();
            int totalMem = (int) (runtime.totalMemory() / 1048576);
            int freeMem = (int) (runtime.freeMemory() / 1048576);
            int usedPercent = (int) (((float) totalMem - freeMem) / (totalMem) * 100.0);

            result.generalStats.put("num-of-cpus",
                    String.valueOf(runtime.availableProcessors()));
            result.generalStats.put("total-avail-memory",
                    String.valueOf(totalMem) + "mb");
            result.generalStats.put("current-memory-usage",
                    String.valueOf(totalMem - freeMem) + "mb" + " ("
                            + usedPercent + "%)");

            return result;
        }
    }

    private Map<String, String> generalStats = new HashMap<String, String>();
    private Map<String, String> applicationStats;
    private InstanceInfo instanceInfo;
    private Boolean isHeathly;

    private StatusInfo() {
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    public boolean isHealthy() {
        return isHeathly.booleanValue();
    }

    public Map<String, String> getGeneralStats() {
        return generalStats;
    }

    public Map<String, String> getApplicationStats() {
        return applicationStats;
    }

    /**
     * Output the amount of time that has elapsed since the given date in the
     * format x days, xx:xx.
     *
     * @return A string representing the formatted interval.
     */
    public static String getUpTime() {
        long diff = ManagementFactory.getRuntimeMXBean().getUptime();
        diff /= 1000 * 60;
        long minutes = diff % 60;
        diff /= 60;
        long hours = diff % 24;
        diff /= 24;
        long days = diff;
        StringBuilder buf = new StringBuilder();
        if (days == 1) {
            buf.append("1 day ");
        } else if (days > 1) {
            buf.append(Long.valueOf(days).toString()).append(" days ");
        }
        DecimalFormat format = new DecimalFormat();
        format.setMinimumIntegerDigits(2);
        buf.append(format.format(hours)).append(":")
                .append(format.format(minutes));
        return buf.toString();
    }

    public static String getCurrentTimeAsString() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        return format.format(new Date());
    }
}

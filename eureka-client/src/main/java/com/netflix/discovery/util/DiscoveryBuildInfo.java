package com.netflix.discovery.util;

import java.io.InputStream;
import java.net.URL;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public final class DiscoveryBuildInfo {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryBuildInfo.class);

    private static final DiscoveryBuildInfo INSTANCE = new DiscoveryBuildInfo(DiscoveryBuildInfo.class);

    private final Manifest manifest;

    /* Visible for testing. */ DiscoveryBuildInfo(Class<?> clazz) {
        Manifest resolvedManifest = null;
        try {
            String jarUrl = resolveJarUrl(clazz);
            if (jarUrl != null) {
                resolvedManifest = loadManifest(jarUrl);
            }
        } catch (Throwable e) {
            logger.warn("Cannot load eureka-client manifest file; no build meta data are available", e);
        }
        this.manifest = resolvedManifest;
    }

    String getBuildVersion() {
        return getManifestAttribute("Implementation-Version", "<version_unknown>");
    }

    String getManifestAttribute(String name, String defaultValue) {
        if (manifest == null) {
            return defaultValue;
        }
        Name attrName = new Name(name);
        Object value = manifest.getMainAttributes().get(attrName);
        return value == null ? defaultValue : value.toString();
    }

    public static String buildVersion() {
        return INSTANCE.getBuildVersion();
    }

    private static Manifest loadManifest(String jarUrl) throws Exception {
        InputStream is = new URL(jarUrl + "!/META-INF/MANIFEST.MF").openStream();
        try {
            return new Manifest(is);
        } finally {
            is.close();
        }
    }

    private static String resolveJarUrl(Class<?> clazz) {
        URL location = clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class");
        if (location != null) {
            Matcher matcher = Pattern.compile("(jar:file.*-[\\d.]+(-rc[\\d]+|-SNAPSHOT)?.jar)!.*$").matcher(location.toString());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}

package com.netflix.discovery.internal.util;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This is an INTERNAL class not for public use.
 *
 * @author David Liu
 */
public final class AmazonInfoUtils {

    public static String readEc2MetadataUrl(MetaDataKey metaDataKey, URL url, int connectionTimeoutMs, int readTimeoutMs) throws IOException {
        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        uc.setConnectTimeout(connectionTimeoutMs);
        uc.setReadTimeout(readTimeoutMs);
        uc.setRequestProperty("User-Agent", "eureka-java-client");

        if (uc.getResponseCode() != HttpURLConnection.HTTP_OK) {  // need to read the error for clean connection close
            BufferedReader br = new BufferedReader(new InputStreamReader(uc.getErrorStream()));
            try {
                while (br.readLine() != null) {
                    // do nothing but keep reading the line
                }
            } finally {
                br.close();
            }
        } else {
           return metaDataKey.read(uc.getInputStream());
        }

        return null;
    }
}

package com.netflix.discovery.converters;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.discovery.EurekaClientConfig;

/**
 * Due to backwards compatibility some names in JSON/XML documents have to be formatted
 * according to a given configuration rules. The formatting functionality is provided by this class.
 *
 * @author Tomasz Bak
 */
@Singleton
public class KeyFormatter {

    public static final String DEFAULT_REPLACEMENT = "__";

    private static final KeyFormatter DEFAULT_KEY_FORMATTER = new KeyFormatter(DEFAULT_REPLACEMENT);

    private final String replacement;

    public KeyFormatter(String replacement) {
        this.replacement = replacement;
    }

    @Inject
    public KeyFormatter(EurekaClientConfig eurekaClientConfig) {
        if (eurekaClientConfig == null) {
            this.replacement = DEFAULT_REPLACEMENT;
        } else {
            this.replacement = eurekaClientConfig.getEscapeCharReplacement();
        }
    }

    public String formatKey(String keyTemplate) {
        StringBuilder sb = new StringBuilder(keyTemplate.length() + 1);
        for (char c : keyTemplate.toCharArray()) {
            if (c == '_') {
                sb.append(replacement);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static KeyFormatter defaultKeyFormatter() {
        return DEFAULT_KEY_FORMATTER;
    }
}

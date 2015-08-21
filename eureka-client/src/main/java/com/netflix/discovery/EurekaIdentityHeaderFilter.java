package com.netflix.discovery;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.apache.http.params.CoreProtocolPNames;

public class EurekaIdentityHeaderFilter extends ClientFilter {

    private final AbstractEurekaIdentity authInfo;
    private final String discoveryAgent;

    public EurekaIdentityHeaderFilter(AbstractEurekaIdentity authInfo) {
        this.authInfo = authInfo;
        this.discoveryAgent = buildUserAgentName();
    }

    @Override
    public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
        if (authInfo != null) {
            cr.getHeaders().putSingle(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY, authInfo.getName());
            cr.getHeaders().putSingle(AbstractEurekaIdentity.AUTH_VERSION_HEADER_KEY, authInfo.getVersion());

            if (authInfo.getId() != null) {
                cr.getHeaders().putSingle(AbstractEurekaIdentity.AUTH_ID_HEADER_KEY, authInfo.getId());
            }
        }
        cr.getHeaders().putSingle(CoreProtocolPNames.USER_AGENT, discoveryAgent);

        return getNext().handle(cr);
    }

    private static String buildUserAgentName() {
        Class<?> clazz = EurekaIdentityHeaderFilter.class;
        StringBuilder sb = new StringBuilder("Java/Netflix EurekaClient");
        URL location = clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class");
        if (location != null) {
            Matcher matcher = Pattern.compile("jar:file.*-([\\d.]+).jar!.*$").matcher(location.toString());
            if (matcher.matches()) {
                sb.append('/').append(matcher.group(1));
            }
        }
        return sb.toString();
    }
}

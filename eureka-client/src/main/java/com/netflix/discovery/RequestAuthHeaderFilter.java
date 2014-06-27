package com.netflix.discovery;

import com.netflix.appinfo.AbstractEurekaAuthInfo;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

public class RequestAuthHeaderFilter extends ClientFilter {

    private final AbstractEurekaAuthInfo authInfo;

    public RequestAuthHeaderFilter(AbstractEurekaAuthInfo authInfo) {
        this.authInfo = authInfo;
    }

    @Override
    public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
        if (authInfo != null) {
            cr.getHeaders().putSingle(AbstractEurekaAuthInfo.AUTH_NAME_HEADER_KEY, authInfo.getName());
            cr.getHeaders().putSingle(AbstractEurekaAuthInfo.AUTH_VERSION_HEADER_KEY, authInfo.getVersion());

            if (authInfo.getId() != null) {
                cr.getHeaders().putSingle(AbstractEurekaAuthInfo.AUTH_ID_HEADER_KEY, authInfo.getId());
            }
        }

        return getNext().handle(cr);
    }
}

package com.netflix.discovery.shared.transport.jersey2;

import com.netflix.appinfo.AbstractEurekaIdentity;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

public class Jersey2EurekaIdentityHeaderFilter implements ClientRequestFilter {

    private final AbstractEurekaIdentity authInfo;

    public Jersey2EurekaIdentityHeaderFilter(AbstractEurekaIdentity authInfo) {
        this.authInfo = authInfo;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (authInfo != null) {

            requestContext.getHeaders().putSingle(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY, authInfo.getName());
            requestContext.getHeaders().putSingle(AbstractEurekaIdentity.AUTH_VERSION_HEADER_KEY, authInfo.getVersion());

            if (authInfo.getId() != null) {
                requestContext.getHeaders().putSingle(AbstractEurekaIdentity.AUTH_ID_HEADER_KEY, authInfo.getId());
            }
        }
    }
}

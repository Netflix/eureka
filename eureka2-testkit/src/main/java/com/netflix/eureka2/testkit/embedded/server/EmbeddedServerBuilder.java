package com.netflix.eureka2.testkit.embedded.server;

import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;

/**
 * @author Tomasz Bak
 */

public abstract class EmbeddedServerBuilder<C extends EurekaServerConfig, B extends EmbeddedServerBuilder<C, B>> {

    protected C configuration;
    protected boolean ext;
    protected NetworkRouter networkRouter;
    protected boolean adminUI;

    public B withConfiguration(C configuration) {
        this.configuration = configuration;
        return self();
    }

    public B withExt(boolean ext) {
        this.ext = ext;
        return self();
    }

    public B withNetworkRouter(NetworkRouter networkRouter) {
        this.networkRouter = networkRouter;
        return self();
    }

    public B withAdminUI(boolean adminUI) {
        this.adminUI = adminUI;
        return self();
    }

    protected B self() {
        return (B) this;
    }
}

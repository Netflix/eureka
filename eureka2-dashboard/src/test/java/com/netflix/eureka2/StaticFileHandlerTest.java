package com.netflix.eureka2;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StaticFileHandlerTest {

    @Test
    public void staticResourceCheck() {
        final MainRequestHandler.StaticFileHandler staticFileHandler = new MainRequestHandler.StaticFileHandler();
        assertTrue(staticFileHandler.isStaticResource("/dashboard.html"));
        assertFalse(staticFileHandler.isStaticResource("/dynamic-resource"));
        assertTrue(staticFileHandler.isStaticResource("/res/static/lib/jquery.js"));
        assertTrue(staticFileHandler.isStaticResource("/res/static/lib/bootstrap.css"));
        assertFalse(staticFileHandler.isStaticResource("/res/static/lib/bootstrap.xml"));
    }

}

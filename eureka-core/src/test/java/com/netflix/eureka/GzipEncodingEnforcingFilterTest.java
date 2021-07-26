/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Kebe Liu
 */
@ExtendWith(MockitoExtension.class)
public class GzipEncodingEnforcingFilterTest {

    private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
    @Mock
    private HttpServletRequest request;

    private HttpServletRequest filteredRequest;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private GzipEncodingEnforcingFilter filter;

    @BeforeEach
    public void setUp() throws Exception {
        filter = new GzipEncodingEnforcingFilter();
        filterChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse response) throws IOException, ServletException {
                filteredRequest = (HttpServletRequest) req;
            }
        };
    }

    @Test
    public void testAlreadyGzip() throws Exception {
        gzipRequest();
        filter.doFilter(request, response, filterChain);
        Enumeration values = filteredRequest.getHeaders(ACCEPT_ENCODING_HEADER);
        assertEquals(null, values, "Expected Accept-Encoding null");
    }

    @Test
    public void testForceGzip() throws Exception {
        noneGzipRequest();
        filter.doFilter(request, response, filterChain);
        String res = "";
        Enumeration values = filteredRequest.getHeaders(ACCEPT_ENCODING_HEADER);
        while (values.hasMoreElements()) {
            res = res + values.nextElement() + "\n";
        }
        assertEquals("gzip\n", res, "Expected Accept-Encoding gzip");
    }

    @Test
    public void testForceGzipOtherHeader() throws Exception {
        noneGzipRequest();
        when(request.getHeader("Test")).thenReturn("ok");
        when(request.getHeaders("Test")).thenReturn(new Enumeration() {
            private int c = 0;

            @Override
            public boolean hasMoreElements() {
                return c == 0;
            }

            @Override
            public Object nextElement() {
                c++;
                return "ok";
            }
        });
        filter.doFilter(request, response, filterChain);
        String res = "";
        Enumeration values = filteredRequest.getHeaders("Test");
        while (values.hasMoreElements()) {
            res = res + values.nextElement() + "\n";
        }
        assertEquals("ok\n", res, "Expected Test ok");
    }

    private void gzipRequest() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(ACCEPT_ENCODING_HEADER)).thenReturn("gzip");
    }

    private void noneGzipRequest() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(ACCEPT_ENCODING_HEADER)).thenReturn(null);
    }
}

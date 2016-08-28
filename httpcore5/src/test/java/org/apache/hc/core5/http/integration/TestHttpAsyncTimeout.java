/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.http.integration;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestProducer;
import org.apache.hc.core5.http.impl.nio.BasicAsyncResponseConsumer;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestHttpAsyncTimeout extends HttpCoreNIOTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {ProtocolScheme.http},
                {ProtocolScheme.https},
        });
    }

    public TestHttpAsyncTimeout(final ProtocolScheme scheme) {
        super(scheme);
    }

    private ServerSocket serverSocket;

    @Override
    protected IOReactorConfig createClientIOReactorConfig() {
        return IOReactorConfig.custom()
                .setIoThreadCount(1)
                .setConnectTimeout(1000)
                .setSoTimeout(1000)
                .build();
    }

    @Before
    public void setUp() throws Exception {
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        serverSocket.close();
        shutDownClient();
    }

    private InetSocketAddress start() throws Exception {

        this.client.start();
        serverSocket = new ServerSocket(0);
        return new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort());
    }

    @Test
    public void testHandshakeTimeout() throws Exception {
        // This test creates a server socket and accepts the incoming
        // socket connection without reading any data.  The client should
        // connect, be unable to progress through the handshake, and then
        // time out when SO_TIMEOUT has elapsed.

        final InetSocketAddress address = start();
        final HttpHost target = new HttpHost("localhost", address.getPort(), getScheme().name());

        final CountDownLatch latch = new CountDownLatch(1);

        final FutureCallback<ClassicHttpResponse> callback = new FutureCallback<ClassicHttpResponse>() {

            @Override
            public void cancelled() {
                latch.countDown();
            }

            @Override
            public void failed(final Exception ex) {
                latch.countDown();
            }

            @Override
            public void completed(final ClassicHttpResponse response) {
                Assert.fail();
            }

        };

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        this.client.execute(
                new BasicAsyncRequestProducer(target, request),
                new BasicAsyncResponseConsumer(),
                context, callback);
        try (final Socket accepted = serverSocket.accept()) {
            Assert.assertTrue(latch.await(10000, TimeUnit.SECONDS));
        }
    }

}

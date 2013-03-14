/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.play;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.websocket.WebSocketProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.F;
import play.mvc.Http;
import play.mvc.WebSocket;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayWebSocket extends org.atmosphere.websocket.WebSocket implements PlayInternal<WebSocket<String>> {
    private static final Logger logger = LoggerFactory.getLogger(PlayWebSocket.class);
    private WebSocket.Out<String> out;
    private WebSocket.In<String> in;

    private final WebSocket<String> w;
    private final AtomicBoolean firstWrite = new AtomicBoolean(false);
    private final WebSocketProcessor webSocketProcessor;

    public PlayWebSocket(final AtmosphereConfig config, final Http.Request request) {
        super(config);

        webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(config.framework());
        w = new WebSocket<String>() {
            @Override
            public void onReady(WebSocket.In<String> iin, Out<String> oout) {
                out = oout;
                in = iin;
                in.onClose(new F.Callback0() {
                    @Override
                    public void invoke() throws Throwable {
                        webSocketProcessor.close(PlayWebSocket.this, 1002);
                    }
                });
                in.onMessage(new F.Callback<String>() {
                    @Override
                    public void invoke(String message) throws Throwable {
                        webSocketProcessor.invokeWebSocketProtocol(PlayWebSocket.this, message);
                    }
                });
                AtmosphereRequest r = null;
                try {
                    r = AtmosphereUtils.request(request);
                } catch (Throwable t) {
                    logger.error("", t);
                }
                try {
                    webSocketProcessor.open(PlayWebSocket.this, r, AtmosphereResponse.newInstance(config, r, PlayWebSocket.this));
                } catch (IOException e) {
                    logger.error("", e);
                    out.close();
                }
            }
        };
    }

    public WebSocket internal() {
        return w;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(String data) throws IOException {
        firstWrite.set(true);
        if (!isOpen()) throw new IOException("Connection remotely closed");

        logger.trace("WebSocket.write()");
        out.write(data);
        lastWrite = System.currentTimeMillis();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(AtmosphereResponse r, byte[] data) throws IOException {
        firstWrite.set(true);
        if (!isOpen()) throw new IOException("Connection remotely closed");

        logger.trace("WebSocket.write()");
        out.write(new String(data, "UTF-8"));
        lastWrite = System.currentTimeMillis();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(byte[] data, int offset, int length) throws IOException {
        firstWrite.set(true);
        if (!isOpen()) throw new IOException("Connection remotely closed");

        out.write(new String(data, offset, length, "UTF-8"));
        return this;
    }

    @Override
    public boolean isOpen() {
        //return in.isOpen();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        out.close();
    }
}



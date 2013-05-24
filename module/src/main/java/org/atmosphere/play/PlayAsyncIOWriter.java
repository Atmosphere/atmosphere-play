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

import org.atmosphere.cpr.*;
import org.atmosphere.util.ByteArrayAsyncWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mvc.Codec;
import play.core.j.JavaResults;
import play.libs.F;
import play.mvc.Http;
import play.mvc.Results;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayAsyncIOWriter extends AtmosphereInterceptorWriter implements PlayInternal<Results.Chunks> {
    private static final Logger logger = LoggerFactory.getLogger(PlayAsyncIOWriter.class);
    private final AtomicInteger pendingWrite = new AtomicInteger();
    private final AtomicBoolean asyncClose = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ByteArrayAsyncWriter buffer = new ByteArrayAsyncWriter();
    protected Results.Chunks<String> chunks;
    protected Results.Chunks.Out<String> out;
    private boolean byteWritten = false;
    private long lastWrite = 0;
    private boolean resumeOnBroadcast;

    public PlayAsyncIOWriter(final Http.Request request, final Http.Response response) {
        chunks = new Results.Chunks<String>(JavaResults.writeString(Codec.utf_8())) {
            @Override
            public void onReady(Results.Chunks.Out<String> oout) {
                out = oout;
                boolean keepAlive = false;

                try {
                    final AtmosphereRequest r = AtmosphereUtils.request(request);
                    out.onDisconnected(new F.Callback0() {
                        @Override
                        public void invoke() throws Throwable {
                            _close(r);
                        }
                    });

                    AtmosphereResponse res = new AtmosphereResponse.Builder()
                            .asyncIOWriter(PlayAsyncIOWriter.this)
                            .writeHeader(false)
                            .request(r).build();
                    keepAlive = AtmosphereCoordinator.instance().route(r, res);
                } catch (Throwable e) {
                    logger.error("", e);
                    keepAlive = true;
                } finally {
                    if (!keepAlive) {
                        out.close();
                    }
                }
            }
        };

        // TODO: Configuring headers in Atmosphere won't work as the onReady is asynchronously called.
        // TODO: Some Broadcaster's Cache won't work as well.
        String[] transport = request.queryString() != null ? request.queryString().get(HeaderConfig.X_ATMOSPHERE_TRANSPORT) : null;
        if (transport != null && transport.length > 0 && transport[0].equalsIgnoreCase(HeaderConfig.SSE_TRANSPORT)) {
            response.setContentType("text/event-stream");
        }
    }

    public Results.Chunks internal() {
        return chunks;
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public boolean byteWritten() {
        return byteWritten;
    }

    public void resumeOnBroadcast(boolean resumeOnBroadcast) {
        this.resumeOnBroadcast = resumeOnBroadcast;
    }

    @Override
    public AsyncIOWriter writeError(AtmosphereResponse r, int errorCode, String message) throws IOException {
//        if (!response.isOpen()) {
//            return this;
//        }

        // TODO: Set status
        logger.error("Error {}:{}", errorCode, message);
        out.write(message);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, String data) throws IOException {
        byte[] b = data.getBytes("ISO-8859-1");
        write(r, b);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data) throws IOException {
        write(r, data, 0, data.length);
        return this;
    }

    protected byte[] transform(AtmosphereResponse response, byte[] b, int offset, int length) throws IOException {
        AsyncIOWriter a = response.getAsyncIOWriter();
        try {
            response.asyncIOWriter(buffer);
            invokeInterceptor(response, b, offset, length);
            return buffer.stream().toByteArray();
        } finally {
            buffer.close(null);
            response.asyncIOWriter(a);
        }
    }

    @Override
    public AsyncIOWriter write(final AtmosphereResponse r, byte[] data, int offset, int length) throws IOException {
        logger.trace("Writing {} with transport {}", r.resource().uuid(), r.resource().transport());
        boolean transform = filters.size() > 0 && r.getStatus() < 400;
        if (transform) {
            data = transform(r, data, offset, length);
            offset = 0;
            length = data.length;
        }

        pendingWrite.incrementAndGet();

        out.write(new String(data, offset, length, r.getCharacterEncoding()));
        byteWritten = true;
        lastWrite = System.currentTimeMillis();
        if (resumeOnBroadcast) {
            out.close();
            _close(r.request());
        }
        return this;
    }

    private void _close(AtmosphereRequest request) {
        final AsynchronousProcessor.AsynchronousProcessorHook hook = (AsynchronousProcessor.AsynchronousProcessorHook)
                request.getAttribute(FrameworkConfig.ASYNCHRONOUS_HOOK);
        if (hook != null) {
            hook.closed();
        } else {
            logger.error("Unable to close properly {}", request.resource().uuid());
        }
    }

    public long lastTick() {
        return lastWrite == -1 ? System.currentTimeMillis() : lastWrite;
    }

    @Override
    public void close(AtmosphereResponse r) throws IOException {
        // Make sure we don't have bufferred bytes
        if (!byteWritten && r != null && r.getOutputStream() != null) {
            r.getOutputStream().flush();
        }

        asyncClose.set(true);
        out.close();
    }
}

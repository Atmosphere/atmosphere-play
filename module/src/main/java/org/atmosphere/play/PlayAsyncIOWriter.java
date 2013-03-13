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

import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.util.ByteArrayAsyncWriter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mvc.Codec;
import play.core.j.JavaResults;
import play.mvc.Http;
import play.mvc.Results;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayAsyncIOWriter extends AtmosphereInterceptorWriter implements PlayInternal<Results.Chunks>{
    private static final Logger logger = LoggerFactory.getLogger(PlayAsyncIOWriter.class);

    private final AtomicInteger pendingWrite = new AtomicInteger();
    private final AtomicBoolean asyncClose = new AtomicBoolean(false);
    private boolean resumeOnBroadcast = false;
    private boolean byteWritten = false;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private long lastWrite = 0;
    private final ByteArrayAsyncWriter buffer = new ByteArrayAsyncWriter();
    protected Results.Chunks<String> chunks;
    protected Results.Chunks.Out<String> out;

    public PlayAsyncIOWriter(final AtmosphereConfig config, final Http.Request request) {
        chunks = new Results.Chunks<String>(JavaResults.writeString(Codec.utf_8()), JavaResults.contentTypeOfString((Codec.utf_8()))) {
            @Override
            public void onReady(Results.Chunks.Out<String> oout) {
                out = oout;
//                out.onDisconnected(new F.Callback0() {
//                    @Override
//                    public void invoke() throws Throwable {
//                        onClose();
//                    }
//                });

                AtmosphereRequest r = null;
                try {
                    r = AtmosphereUtils.request(request);
                } catch (Throwable t) {
                    logger.error("",t);
                }

                AtmosphereResponse res = new AtmosphereResponse.Builder()
                        .asyncIOWriter(PlayAsyncIOWriter.this)
                        .writeHeader(false)
                        .request(r).build();
                try {
                    config.framework().doCometSupport(r, res);
                } catch (Throwable e) {
                    logger.error("", e);
                }
            }
        };
    }

    public Results.Chunks internal(){
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
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data, int offset, int length) throws IOException {

        boolean transform = filters.size() > 0 && r.getStatus() < 400;
        if (transform) {
            data = transform(r, data, offset, length);
            offset = 0;
            length = data.length;
        }

        final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        pendingWrite.incrementAndGet();

        out.write(new String(data, offset, length, r.getCharacterEncoding()));
        byteWritten = true;
        lastWrite = System.currentTimeMillis();
        return this;
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

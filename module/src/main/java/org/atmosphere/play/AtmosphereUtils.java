/*
 * Copyright 2018 Async-IO.org
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

import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereRequestImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class AtmosphereUtils {

    private static Logger logger = LoggerFactory.getLogger(AtmosphereUtils.class);

    public final static AtmosphereRequest request(final Http.Request request, final Map<String, Object> additionalAttributes) throws Throwable {
        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.uri());
        String ct = "text/plain";
        if (request.getHeader("Content-Type") != null) {
            ct = request.getHeader("Content-Type");
        }
        String method = request.method();

        String queryString = requestUri.getQuery();
        Map<String, String[]> qs = new HashMap<String, String[]>();
        if (queryString != null) {
            parseQueryString(qs, queryString);
        }

        if (ct.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            parseQueryString(qs, request.body().asText());
        }

        String u = requestUri.toURL().toString();
        int last = u.indexOf("?") == -1 ? u.length() : u.indexOf("?");
        String url = u.substring(0, last);
        int l = requestUri.getAuthority().length() + requestUri.getScheme().length() + 3;

        final Map<String, Object> attributes = new HashMap<String, Object>(additionalAttributes);

        boolean hasBody = request.body() != null;
        byte[] body = {};

        // This is crazy: asRaw return null with Firefox + SSE
        // TODO: char encoding issue, needs to decode from content-type.
        if (hasBody) {
            if (request.body().asText() != null) {
                body = request.body().asText().getBytes("utf-8");
            } else if (request.body().asRaw() != null) {
                body = request.body().asBytes().toArray();
            } else if (request.body().asJson() != null) {
                body = request.body().asJson().asText().getBytes("utf-8");
                if (body.length == 0) {
                    body = request.body().asJson().toString().getBytes("utf-8");
                }
            } else if (request.body().asXml() != null) {
                body = request.body().asXml().getTextContent().getBytes("utf-8");
            }
        }

        URI uri = null;
        try {
            URI.create(request.remoteAddress());
        } catch (IllegalArgumentException e) {
            logger.trace("", e);
        }

        int port = uri == null ? 0 : uri.getPort();
        String uriString = uri == null ? request.remoteAddress() : uri.toString();
        String host = uri == null ? request.remoteAddress() : uri.getHost();
        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequestImpl.Builder();
        AtmosphereRequest r = requestBuilder.requestURI(url.substring(l))
                .requestURL(u)
                .pathInfo(url.substring(l))
                .headers(getHeaders(request))
                .method(method)
                .contentType(ct)
                .destroyable(false)
                .attributes(attributes)
                .servletPath("")
                .queryStrings(qs)
                .remotePort(port)
                .remoteAddr(uriString)
                .remoteHost(host)
                        //                .localPort(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getPort())
                        //                .localAddr(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getAddress().getHostAddress())
                        //                .localName(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getHostName())
                .inputStream(hasBody ? new ByteArrayInputStream(body) : new ByteArrayInputStream(new byte[]{}))
                .build();

        return r;
    }


    public static String getBaseUri(final Http.Request request) {
        return "http://" + request.getHeader(Http.HeaderNames.HOST) + "/";

    }

    public static void parseQueryString(Map<String, String[]> qs, String queryString) {
        if (queryString != null) {
            String[] s = queryString.split("&");
            for (String a : s) {
                String[] q = a.split("=");
                String[] z = new String[]{q.length > 1 ? q[1] : ""};
                qs.put(q[0], z);
            }
        }
    }

    public static Map<String, String> getHeaders(final Http.Request request) {
        final Map<String, String> headers = new HashMap<String, String>();

        for (Map.Entry<String, String[]> e : request.headers().entrySet()) {
            headers.put(e.getKey().toLowerCase(), e.getValue()[0]);
        }

        return headers;
    }
}

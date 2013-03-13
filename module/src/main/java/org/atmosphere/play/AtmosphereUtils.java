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

import org.atmosphere.cpr.AtmosphereRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import play.mvc.Http;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class AtmosphereUtils {

    public final static AtmosphereRequest request(final Http.Request request) throws Throwable {
        final String base = getBaseUri(request);
        final URI requestUri = new URI(base.substring(0, base.length() - 1) + request.uri());
        String ct = "text/plain";
        if (request.getHeader("Content-Type") != null && request.headers().get("Content-Type").length > 0) {
            ct = request.headers().get("Content-Type")[0];
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

        final Map<String, Object> attributes = new HashMap<String, Object>();

        URI uri = URI.create(request.remoteAddress());
        AtmosphereRequest.Builder requestBuilder = new AtmosphereRequest.Builder();
        AtmosphereRequest r = requestBuilder.requestURI(url.substring(l))
                .requestURL(url)
                .pathInfo(url.substring(l))
                .headers(getHeaders(request))
                .method(method)
                .contentType(ct)
                .destroyable(false)
                .attributes(attributes)
                .servletPath("")
                .queryStrings(qs)
                .remotePort(uri.getPort())
                .remoteAddr(uri.toString())
                .remoteHost(uri.getHost())
                        //                .localPort(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getPort())
                        //                .localAddr(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getAddress().getHostAddress())
                        //                .localName(((InetSocketAddress) ctx.getChannel().getLocalAddress()).getHostName())
                .inputStream(request.body() != null ? new ByteArrayInputStream(request.body().asRaw().asBytes()) : new ByteArrayInputStream(new byte[]{}))
                .build();

        return r;
    }


    public static String getBaseUri(final Http.Request request) {
        return "http://" + request.getHeader(HttpHeaders.Names.HOST) + "/";

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
            headers.put(e.getKey(), e.getValue()[0]);
        }

        return headers;
    }
}

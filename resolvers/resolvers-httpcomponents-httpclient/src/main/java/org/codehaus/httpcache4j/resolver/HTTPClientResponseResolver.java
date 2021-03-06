/*
 * Copyright (c) 2009. The Codehaus. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.codehaus.httpcache4j.resolver;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.codehaus.httpcache4j.*;
import org.codehaus.httpcache4j.Header;
import org.codehaus.httpcache4j.StatusLine;
import org.codehaus.httpcache4j.auth.*;
import org.codehaus.httpcache4j.payload.DelegatingInputStream;
import org.apache.http.client.methods.*;
import org.apache.http.*;
import org.apache.http.entity.InputStreamEntity;
import org.codehaus.httpcache4j.payload.Payload;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * @author <a href="mailto:hamnis@codehaus.org">Erlend Hamnaberg</a>
 * @version $Revision: #5 $ $Date: 2008/09/15 $
 */
public class HTTPClientResponseResolver extends AbstractResponseResolver {
    private final CloseableHttpClient httpClient;


    /**
     * Turns off the automatic authentication and redirection support of the supplied HttpClient.
     * Overrides the default proxy support with the supplied configuration in ResolverConfiguration.
     * Sets the user agent with the configured user agent.
     *
     */
    public HTTPClientResponseResolver(CloseableHttpClient httpClient, ResolverConfiguration configuration) {
        super(configuration);
        this.httpClient = httpClient;
    }

    public HTTPClientResponseResolver(CloseableHttpClient httpClient, ProxyAuthenticator proxyAuthenticator, Authenticator authenticator) {
        this(httpClient, new ResolverConfiguration(proxyAuthenticator, authenticator, new ConnectionConfiguration()));
    }
    
    public HTTPClientResponseResolver(CloseableHttpClient httpClient, ProxyConfiguration proxyConfiguration) {
        this(httpClient, new DefaultProxyAuthenticator(proxyConfiguration), new DefaultAuthenticator());
    }

    public HTTPClientResponseResolver(CloseableHttpClient httpClient) {
        this(httpClient, new ProxyConfiguration());
    }

    public static HTTPClientResponseResolver createMultithreadedInstance(ConnectionConfiguration config) {
        return createMultithreadedInstance(new ResolverConfiguration().withConnectionConfiguration(config));
    }

    public static HTTPClientResponseResolver createMultithreadedInstance() {
        return createMultithreadedInstance(new ConnectionConfiguration());
    }

    public static HTTPClientResponseResolver createMultithreadedInstance(ResolverConfiguration configuration) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        CloseableHttpClient client = new HttpClientFactory().configure(builder, configuration);
        return new HTTPClientResponseResolver(client, configuration);
    }

    public final CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    protected HTTPResponse resolveImpl(HTTPRequest request) throws IOException {
        HttpUriRequest realRequest = convertRequest(request);
        HttpResponse response = httpClient.execute(realRequest);
        return convertResponse(realRequest, response);
    }

    @Deprecated
    public void setRedirecting(boolean redirect) {
        throw new UnsupportedOperationException("You need to set this when creating the HttpClient, sorry!");
    }

    public void shutdown() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                throw new HTTPException(e);
            }
        }
    }

    private HttpUriRequest convertRequest(HTTPRequest request) {
        HttpUriRequest realRequest = getMethod(request.getMethod(), request.getNormalizedURI());

        Headers headers = request.getAllHeaders();
        for (Header header : headers) {
            realRequest.addHeader(header.getName(), header.getValue());
        }

        if (request.hasPayload() && realRequest instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest req = (HttpEntityEnclosingRequest) realRequest;
            req.setEntity(new PayloadEntity(request.getPayload(), getConfiguration().isUseChunked()));
        }
        return realRequest;
    }

    protected HttpUriRequest getMethod(HTTPMethod method, URI requestURI) {
        if (method.canHavePayload()) {
            return new MethodWithBody(requestURI, method);
        }
        else {
            return new Method(requestURI, method);
        }
    }

    private HTTPResponse convertResponse(HttpUriRequest request, HttpResponse response) throws IOException {
        Headers headers = new Headers();
        org.apache.http.Header[] realHeaders = response.getAllHeaders();
        for (org.apache.http.Header header : realHeaders) {
            headers = headers.add(header.getName(), header.getValue());
        }

        InputStream stream = getStream(request, response);
        ProtocolVersion protocolversion = response.getStatusLine().getProtocolVersion();
        StatusLine line = new StatusLine(
                HTTPVersion.get(protocolversion.getMajor() + "." + protocolversion.getMinor()),
                Status.valueOf(response.getStatusLine().getStatusCode()),
                response.getStatusLine().getReasonPhrase());
        return getResponseCreator().createResponse(line, headers, stream);
    }

    private InputStream getStream(HttpUriRequest realRequest, HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return null;
        }
        try {
            return new HttpEntityInputStream(entity);
        } catch (IOException e) {
            realRequest.abort();
            throw e;
        } catch (RuntimeException e) {
            realRequest.abort();
            throw e;
        }
    }

    private static class HttpEntityInputStream extends DelegatingInputStream {
        private final HttpEntity entity;

        public HttpEntityInputStream(HttpEntity entity) throws IOException {
            super(entity.getContent());
            this.entity = entity;
        }

        @Override
        public void close() throws IOException {
            EntityUtils.consume(entity);
        }
    }

    private static class PayloadEntity extends InputStreamEntity {
        public PayloadEntity(final Payload payload, boolean chunked) {
            super(payload.getInputStream(), chunked ? -1 : payload.length());
            setContentType(payload.getMimeType().toString());
            setChunked(chunked);
        }
    }

    @NotThreadSafe
    private static class Method extends HttpRequestBase {

        private HTTPMethod method;

        public Method(final URI uri, HTTPMethod method) {
            super();
            this.method = method;
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return method.getMethod();
        }

    }

    @NotThreadSafe
    private static class MethodWithBody extends HttpEntityEnclosingRequestBase {

        private HTTPMethod method;

        public MethodWithBody(final URI uri, HTTPMethod method) {
            super();
            this.method = method;
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return method.getMethod();
        }

    }
}

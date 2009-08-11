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

package org.codehaus.httpcache4j.cache;

import org.codehaus.httpcache4j.payload.Payload;
import org.codehaus.httpcache4j.HTTPResponse;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.IOException;

/**
 * @author <a href="mailto:hamnis@codehaus.org">Erlend Hamnaberg</a>
 * @version $Revision: $
 */
public abstract class AbstractMapBasedCacheStorage extends AbstractCacheStorage {

    protected HTTPResponse rewriteResponse(Key key, HTTPResponse response) {
        if (response.hasPayload()) {
            Payload payload = response.getPayload();
            InputStream stream = null;
            try {
                stream = payload.getInputStream();
                return new HTTPResponse(createPayload(key, payload, stream), response.getStatus(), response.getHeaders());
            } catch (IOException ignore) {
            }
            finally {
                IOUtils.closeQuietly(stream);
            }
        }
        else {
            return response;
        }
        throw new IllegalArgumentException("Unable to cache response");
    }

    public final HTTPResponse update(Key key, HTTPResponse response) {
        return putImpl(key, response);
    }

    protected abstract Payload createPayload(Key key, Payload payload, InputStream stream) throws IOException;
    
    
}

/**
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.junit.v2;

import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.codec.SpanBytesCodec;
import zipkin.internal.v2.storage.SpanConsumer;

/** Implements the span consumer interface by forwarding requests over http. */
final class HttpV2SpanConsumer implements SpanConsumer {
  final HttpV2Call.Factory factory;

  HttpV2SpanConsumer(OkHttpClient client, HttpUrl baseUrl) {
    this.factory = new HttpV2Call.Factory(client, baseUrl);
  }

  @Override public zipkin.internal.v2.Call<Void> accept(List<Span> spans) {
    byte[] json = SpanBytesCodec.JSON_V2.encodeList(spans);
    return factory.newCall(new Request.Builder()
        .url(factory.baseUrl.resolve("/api/v2/spans"))
        .post(RequestBody.create(MediaType.parse("application/json"), json)).build(),
      b -> null /* void */
    );
  }
}

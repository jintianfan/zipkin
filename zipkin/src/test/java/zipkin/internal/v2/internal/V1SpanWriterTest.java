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
package zipkin.internal.v2.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.TraceKeys;
import zipkin.internal.v2.Endpoint;
import zipkin.internal.v2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class V1SpanWriterTest {
  V1SpanWriter writer = new V1SpanWriter();
  Buffer buf = new Buffer(2048); // bigger than needed to test sizeOf

  Endpoint frontend = Endpoint.newBuilder()
    .serviceName("frontend")
    .ip("127.0.0.1")
    .build();
  Endpoint backend = Endpoint.newBuilder()
    .serviceName("backend")
    .ip("192.168.99.101")
    .port(9000)
    .build();

  // TODO: check for core annotation!
  Span span = Span.newBuilder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("6b221d5bc9e6496c")
    .id("5b4185666d50f68b")
    .name("get")
    .kind(Span.Kind.CLIENT)
    .localEndpoint(frontend)
    .remoteEndpoint(backend)
    .timestamp(1472470996199000L)
    .duration(207000L)
    .addAnnotation(1472470996238000L, "foo")
    .putTag(TraceKeys.HTTP_PATH, "/api")
    .putTag("clnt/finagle.version", "6.45.0")
    .build();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void sizeInBytes() throws IOException {
    writer.write(span, buf);

    assertThat(writer.sizeInBytes(span))
      .isEqualTo(buf.pos);
  }

  @Test public void writesCoreAnnotations_client() throws IOException {
    writer.write(span, buf);

    writesCoreAnnotations("cs", "cr");
  }

  @Test public void writesCoreAnnotations_server() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.SERVER).build(), buf);

    writesCoreAnnotations("sr", "ss");
  }

  @Test public void writesCoreAnnotations_producer() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.PRODUCER).build(), buf);

    writesCoreAnnotations("ms", "ws");
  }

  @Test public void writesCoreAnnotations_consumer() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.CONSUMER).build(), buf);

    writesCoreAnnotations("wr", "mr");
  }

  void writesCoreAnnotations(String begin, String end) throws UnsupportedEncodingException {
    String json = new String(buf.toByteArray(), "UTF-8");

    assertThat(json).contains(
      "{\"timestamp\":" + span.timestamp() + ",\"value\":\"" + begin + "\"");
    assertThat(json).contains(
      "{\"timestamp\":" + (span.timestamp() + span.duration()) + ",\"value\":\"" + end + "\"");
  }

  @Test public void writesCoreSendAnnotations_client() throws IOException {
    writer.write(span.toBuilder().duration(null).build(), buf);

    writesCoreSendAnnotations("cs");
  }

  @Test public void writesCoreSendAnnotations_server() throws IOException {
    writer.write(span.toBuilder().duration(null).kind(Span.Kind.SERVER).build(), buf);

    writesCoreSendAnnotations("sr");
  }

  @Test public void writesCoreSendAnnotations_producer() throws IOException {
    writer.write(span.toBuilder().duration(null).kind(Span.Kind.PRODUCER).build(), buf);

    writesCoreSendAnnotations("ms");
  }

  @Test public void writesCoreSendAnnotations_consumer() throws IOException {
    writer.write(span.toBuilder().duration(null).kind(Span.Kind.CONSUMER).build(), buf);

    writesCoreSendAnnotations("mr");
  }

  void writesCoreSendAnnotations(String begin) throws UnsupportedEncodingException {
    String json = new String(buf.toByteArray(), "UTF-8");

    assertThat(json).contains(
      "{\"timestamp\":" + span.timestamp() + ",\"value\":\"" + begin + "\"");
  }

  @Test public void writesAddressBinaryAnnotation_client() throws IOException {
    writer.write(span.toBuilder().build(), buf);

    writesAddressBinaryAnnotation("sa");
  }

  @Test public void writesAddressBinaryAnnotation_server() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.SERVER).build(), buf);

    writesAddressBinaryAnnotation("ca");
  }

  @Test public void writesAddressBinaryAnnotation_producer() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.PRODUCER).build(), buf);

    writesAddressBinaryAnnotation("ma");
  }

  @Test public void writesAddressBinaryAnnotation_consumer() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.CONSUMER).build(), buf);

    writesAddressBinaryAnnotation("ma");
  }

  void writesAddressBinaryAnnotation(String address) throws UnsupportedEncodingException {
    String json = new String(buf.toByteArray(), "UTF-8");

    assertThat(json)
      .contains("{\"key\":\"" + address + "\",\"value\":true,\"endpoint\":");
  }

  @Test public void writes128BitTraceId() throws UnsupportedEncodingException {
    String traceId = "48485a3953bb61246b221d5bc9e6496c";
    span = Span.newBuilder().traceId(traceId).id("1").build();

    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .startsWith("{\"traceId\":\"" + traceId + "\"");
  }

  @Test public void annotationsHaveEndpoints() throws IOException {
    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8")).contains(
      "\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test public void writesTimestampAndDuration() throws IOException {
    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("\"timestamp\":" + span.timestamp() + ",\"duration\":" + span.duration());
  }

  @Test public void skipsTimestampAndDuration_shared() throws IOException {
    writer.write(span.toBuilder().kind(Span.Kind.SERVER).shared(true).build(), buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .doesNotContain("\"timestamp\":" + span.timestamp() + ",\"duration\":" + span.duration());
  }

  @Test public void writesEmptySpanName() throws IOException {
    span = Span.newBuilder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("6b221d5bc9e6496c")
      .id("5b4185666d50f68b")
      .build();

    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("\"name\":\"\"");
  }

  @Test public void writesEmptyServiceName() throws IOException {
    span = span.toBuilder()
      .localEndpoint(Endpoint.newBuilder().ip("127.0.0.1").build())
      .build();

    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test public void tagsAreBinaryAnnotations() throws IOException {
    writer.write(span, buf);

    assertThat(new String(buf.toByteArray(), "UTF-8"))
      .contains("\"binaryAnnotations\":["
        +
        "{\"key\":\"clnt/finagle.version\",\"value\":\"6.45.0\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}},"
        +
        "{\"key\":\"http.path\",\"value\":\"/api\",\"endpoint\":{\"serviceName\":\"frontend\",\"ipv4\":\"127.0.0.1\"}}");
  }
}

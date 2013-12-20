/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.httpserver;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.httpserver.TracesHelper.TraceAttributes;
import com.google.common.base.Optional;

import org.easymock.EasyMockSupport;
import org.eclipse.jetty.server.Request;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TracesHandlerTest extends EasyMockSupport {

  @Test
  public void testHandleGet() throws IOException {
    File a = createMock(File.class);
    expect(a.lastModified()).andStubReturn(1000L);
    expect(a.getName()).andStubReturn("build.a.trace");

    File b = createMock(File.class);
    expect(b.lastModified()).andStubReturn(4000L);
    expect(b.getName()).andStubReturn("build.b.trace");

    File c = createMock(File.class);
    expect(c.lastModified()).andStubReturn(2000L);
    expect(c.getName()).andStubReturn("build.c.trace");

    File d = createMock(File.class);
    expect(d.lastModified()).andStubReturn(3000L);
    expect(d.getName()).andStubReturn("build.d.trace");

    TracesHelper tracesHelper = createMock(TracesHelper.class);
    expect(tracesHelper.getTraceAttributesFor(a)).andReturn(
        new TraceAttributes(Optional.of("buck build buck"), 1000L));
    expect(tracesHelper.getTraceAttributesFor(b)).andReturn(
        new TraceAttributes(Optional.of("buck test --all --code-coverage"), 4000L));
    expect(tracesHelper.getTraceAttributesFor(c)).andReturn(
        new TraceAttributes(Optional.<String>absent(), 2000L));
    expect(tracesHelper.getTraceAttributesFor(d)).andReturn(
        new TraceAttributes(Optional.of("buck test --all --code-coverage"), 3000L));

    expect(tracesHelper.listTraceFiles()).andReturn(new File[] {a, b, c, d});

    Request baseRequest = createMock(Request.class);

    replayAll();

    TracesHandlerDelegate delegate = new TracesHandlerDelegate(tracesHelper);
    TemplateHandler tracesHandler = new TemplateHandler(delegate);
    String html = tracesHandler.createHtmlForResponse(baseRequest);

    int indexB = html.indexOf("<a href=\"/trace/b\" target=\"_blank\">build.b.trace</a>");
    assertTrue(indexB > 0);

    int indexD = html.indexOf("<a href=\"/trace/d\" target=\"_blank\">build.d.trace</a>");
    assertTrue(indexD > indexB);

    int indexC = html.indexOf("<a href=\"/trace/c\" target=\"_blank\">build.c.trace</a>");
    assertTrue(indexC > indexD);

    int indexA = html.indexOf("<a href=\"/trace/a\" target=\"_blank\">build.a.trace</a>");
    assertTrue(indexA > indexC);

    verifyAll();
  }
}

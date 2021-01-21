//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

import javax.websocket.ContainerProvider;

import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;

module org.eclipse.jetty.websocket.javax.client
{
    exports org.eclipse.jetty.websocket.javax.client;
    exports org.eclipse.jetty.websocket.javax.client.internal to org.eclipse.jetty.websocket.javax.server;

    requires org.eclipse.jetty.client;
    requires org.eclipse.jetty.websocket.core.client;
    requires org.eclipse.jetty.websocket.javax.common;
    requires transitive jetty.websocket.api;

    provides ContainerProvider with JavaxWebSocketClientContainerProvider;
}

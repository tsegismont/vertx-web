/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.web.handler;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.ext.web.handler.impl.LoggerHandlerImpl;

/**
 * A handler which logs request information to the Vert.x logger.
 * You should mount this handler before any handler that could fail the routing context.
 * Logs will be produced with the appropriate severity level depending on the status code of the response.
 * To capture the logs configure the logger in your logging configuration with the name:
 * {@code io.vertx.ext.web.handler.LoggerHandler}.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:pmlopes@gmail.com">Paulo Lopes</a>
 */
@VertxGen
public interface LoggerHandler extends PlatformHandler {

  LoggerFormat DEFAULT_FORMAT = LoggerFormat.DEFAULT;

  /**
   * Create a handler with default format
   *
   * @return the handler
   */
  static LoggerHandler create() {
    return new LoggerHandlerImpl(DEFAULT_FORMAT);
  }

  /**
   * Create a handler with he specified format
   *
   * @param format  the format
   * @return the handler
   */
  static LoggerHandler create(LoggerFormat format) {
    return new LoggerHandlerImpl(format);
  }

  /**
   * Create a handler with he specified format
   *
   * @param immediate  true if logging should occur as soon as request arrives
   * @param format  the format
   * @return the handler
   */
  static LoggerHandler create(boolean immediate, LoggerFormat format) {
    return new LoggerHandlerImpl(immediate, format);
  }

  /**
   * Set the custom formatter to be used by the handler.
   *
   * @param formatter the formatter
   * @return the formatted log string
   * @throws IllegalStateException if current format is not {@link LoggerFormat#CUSTOM}
   */
  @Fluent
  LoggerHandler customFormatter(LoggerFormatter formatter);
}

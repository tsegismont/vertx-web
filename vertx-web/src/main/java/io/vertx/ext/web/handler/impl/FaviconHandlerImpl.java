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

package io.vertx.ext.web.handler.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.RoutingContext;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FaviconHandlerImpl implements FaviconHandler {

  private static final Logger LOG = LoggerFactory.getLogger(FaviconHandler.class);

  // default framework branding
  private static final String DEFAULT_VERTX_ICON = "META-INF/vertx/web/favicon.ico";

  private final Icon NULL_ICON = new Icon();

  /**
   * ## Icon
   *
   * Represents a favicon.ico file and related headers
   */
  private class Icon {
    /**
     * Headers for the icon resource
     */
    private final MultiMap headers;

    /**
     * Binary content of the icon file
     */
    private final Buffer body;

    /**
     * Instantiate a new Icon
     *
     * @param buffer buffer containing the image data for this icon.
     */
    private Icon(Buffer buffer) {
      headers = HttpHeaders.headers();
      body = buffer;

      headers.add(CONTENT_TYPE, "image/x-icon");
      headers.add(CONTENT_LENGTH, Integer.toString(buffer.length()));
      headers.add(CACHE_CONTROL, "public, max-age=" + maxAgeSeconds);
    }

    private Icon() {
      headers = null;
      body = null;
    }
  }

  /**
   * favicon cache
   */
  private final Icon icon;

  /**
   * Cache control for the resource
   */
  private final long maxAgeSeconds;

  /**
   * Create a new Favicon instance using a file in the file system and customizable cache period
   *
   * <pre>
   * Router router = Router.router(vertx);
   * router.route().handler(FaviconHandler.create(vertx, "/icons/icon.ico", 1000));
   * </pre>
   *
   * @param path file path to icon
   * @param maxAgeSeconds max allowed time to be cached in seconds
   */
  public FaviconHandlerImpl(Vertx vertx, String path, long maxAgeSeconds) {
    this.maxAgeSeconds = maxAgeSeconds;
    if (maxAgeSeconds < 0) {
      throw new IllegalArgumentException("maxAgeSeconds must be > 0");
    }

    FileSystem fs = vertx.fileSystem();

    Buffer buffer = null;

    if (path == null) {
      // use defaults
      try {
        // default is to load a favicon.ico from the resources
        buffer = readFile(fs,"favicon.ico");
      } catch (RuntimeException e) {
        // if not provided return the default vertx icon
        try {
          buffer = fs.readFileBlocking(DEFAULT_VERTX_ICON);
        } catch (RuntimeException e1) {
          // if this fails (very unlikely unless users explicitly filters out the icon)
          // leave the buffer as NULL so it is handled as a 404 (Not Found)
        }
      }
    } else {
      // use a custom location for the favicon.ico
      try {
        buffer = readFile(fs, path);
      } catch (RuntimeException e) {
        LOG.error("Could not load favicon " + path);
        // this will ensure the response is a 404 (Not Found)
      }
    }

    if (buffer != null) {
      icon = new Icon(buffer);
    } else {
      icon = NULL_ICON;
    }
  }

  /**
   * Create a new Favicon instance from the classpath and customizable cache period
   *
   * <pre>
   * Router router = Router.router(vertx);
   * router.route().handler(FaviconHandler.create(vertx, 1000));
   * </pre>
   *
   * @param maxAgeSeconds max allowed time to be cached in seconds
   */
  public FaviconHandlerImpl(Vertx vertx, long maxAgeSeconds) {
    this(vertx, null, maxAgeSeconds);
  }

  /**
   * Create a new Favicon instance using a file in the file system and cache for 1 day.
   *
   * <pre>
   * Router router = Router.router(vertx);
   * router.route().handler(FaviconHandler.create(vertx, "/icons/icon.ico"));
   * </pre>
   *
   * @param path file path to icon
   */
  public FaviconHandlerImpl(Vertx vertx, String path) {
    this(vertx, path, DEFAULT_MAX_AGE_SECONDS);
  }

  /**
   * Create a new Favicon instance using a the default icon and cache for 1 day.
   *
   * <pre>
   * Router router = Router.router(vertx);
   * router.route().handler(FaviconHandler.create(vertx));
   * </pre>
   */
  public FaviconHandlerImpl(Vertx vertx) {
    this(vertx, null);
  }

  private Buffer readFile(FileSystem fs, String path) {
    if (fs.existsBlocking(path)) {
      return fs.readFileBlocking(path);
    } else {
      throw new RuntimeException(path + " not found!");
    }
  }

  @Override
  public void handle(RoutingContext ctx) {
    if ("/favicon.ico".equals(ctx.normalizedPath())) {
      HttpServerResponse resp = ctx.response();
      if (icon == NULL_ICON) {
        resp.setStatusCode(404).end();
      } else {
        resp.headers().addAll(icon.headers);
        resp.end(icon.body);
      }
    } else {
      ctx.next();
    }
  }
}

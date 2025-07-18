package io.vertx.ext.web.openapi.router.impl;

import io.vertx.core.Future;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.SimpleAuthenticationHandler;
import io.vertx.ext.web.internal.handler.ScopedAuthentication;
import io.vertx.openapi.contract.Operation;
import io.vertx.openapi.contract.SecurityRequirement;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author Francesco Guardiani @slinkydeveloper
 * @author Paulo Lopes
 */
class AuthenticationHandlers {

  private static final AuthenticationHandler ANONYMOUS_SUCCESS_AUTH_HANDLER =
    SimpleAuthenticationHandler
      .create()
      .authenticate(ctx -> Future.succeededFuture());

  private final Map<String, List<AuthenticationHandler>> securityHandlers;
  private final Map<String, OAuth2AuthHandler> callbackHandlers;

  AuthenticationHandlers() {
    this.securityHandlers = new HashMap<>();
    this.callbackHandlers = new HashMap<>();
  }

  protected void addRequirement(String name, AuthenticationHandler handler, String callback) {
    securityHandlers
      .computeIfAbsent(name, k -> new ArrayList<>())
      .add(handler);

    if (callback != null) {
      // the problem, when by mistake a user defines 2 different handlers with the same callback,
      // the callback would always redirect to the 1st handler and not the 2nd as expected.
      if (callbackHandlers.containsKey(callback)) {
        throw new IllegalStateException("Callback already in use: " + callback +
          " [only 1 callback per handler is allowed]");
      }
      callbackHandlers.put(callback, (OAuth2AuthHandler) handler);
    }
  }

  /**
   * The input array is an OR of different AND security requirements
   */
  protected void solve(Operation operation, Route route, boolean failOnNotFound) {
    AuthenticationHandler authn = or(route, operation.getSecurityRequirements(), failOnNotFound);

    if (authn != null) {
      route.handler(authn);
    }
  }

  private List<AuthenticationHandler> resolveHandlers(Route route, String name, List<String> scopes,
                                                      boolean failOnNotFound) {
    List<AuthenticationHandler> authenticationHandlers;
    if (failOnNotFound) {
      authenticationHandlers = Optional
        .ofNullable(this.securityHandlers.get(name))
        .orElseThrow(() -> new IllegalStateException("Missing security handler for: '" + name + "'"));
    } else {
      authenticationHandlers = Optional
        .ofNullable(this.securityHandlers.get(name))
        .orElse(Collections.emptyList());
    }

    // Some scopes are defines, we need to configure them in OAuth2Handlers
    if (!scopes.isEmpty()) {
      route.putMetadata("scopes", scopes);

      // Update the returned list to have handlers with the required scopes
      authenticationHandlers = authenticationHandlers
        .stream()
        .map(authHandler -> {
          if (authHandler instanceof ScopedAuthentication<?>) {
            return ((ScopedAuthentication<?>) authHandler).withScopes(scopes);
          } else {
            return authHandler;
          }
        })
        .collect(Collectors.toList());
    }

    return authenticationHandlers;
  }

  private AuthenticationHandler and(Route route, SecurityRequirement securityRequirement, boolean failOnNotFound) {
    List<AuthenticationHandler> handlers = securityRequirement.getNames()
      .stream()
      .flatMap(name -> resolveHandlers(route, name, securityRequirement.getScopes(name), failOnNotFound).stream())
      .collect(Collectors.toList());

    if (handlers.isEmpty()) {
      return null;
    }

    if (handlers.size() == 1) {
      return handlers.get(0);
    }

    ChainAuthHandler authHandler = ChainAuthHandler.all();
    handlers.forEach(authHandler::add);

    return authHandler;
  }

  private AuthenticationHandler or(Route route, List<SecurityRequirement> securityRequirements,
                                   boolean failOnNotFound) {
    if (securityRequirements == null || securityRequirements.isEmpty()) {
      return null;
    }

    boolean emptyAuth = false;
    for (int i = 0; i < securityRequirements.size(); i++) {
      if (securityRequirements.get(i).isEmpty()) {
        emptyAuth = true;
        // we will modify the list, so we need to copy it
        securityRequirements = new ArrayList<>(securityRequirements);
        securityRequirements.remove(i);
        break;
      }
    }

    final ChainAuthHandler authHandler;
    final AtomicBoolean emptyAuthHandler = new AtomicBoolean(true);

    switch (securityRequirements.size()) {
      case 0:
        return null;
      case 1:
        if (!emptyAuth) {
          // If one security requirements, we don't need a ChainAuthHandler
          return and(route, securityRequirements.get(0), failOnNotFound);
        }
      default:
        authHandler = ChainAuthHandler.any();
        securityRequirements
          .stream()
          .map(securityRequirement -> and(route, securityRequirement, failOnNotFound))
          .filter(Objects::nonNull)
          .forEach(handler -> {
              authHandler.add(handler);
              emptyAuthHandler.set(false);
          });
    }

    if (emptyAuth || emptyAuthHandler.get()) {
      authHandler.add(ANONYMOUS_SUCCESS_AUTH_HANDLER);
    }

    return authHandler;
  }

  public void applyCallbackHandlers(Router router) {
    callbackHandlers.forEach((path, handler) -> {
      handler.setupCallback(router.get(path));
    });
  }
}

/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.ext.web.handler.graphql;

import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.graphql.instrumentation.JsonObjectAdapter;
import org.junit.Test;

import java.util.List;

import static graphql.schema.idl.RuntimeWiring.*;
import static io.vertx.core.http.HttpMethod.*;

/**
 * @author Thomas Segismont
 */
public class JsonResultsTest extends GraphQLTestBase {

  @Override
  protected GraphQL graphQL() {
    String schema = vertx.fileSystem().readFileBlocking("links.graphqls").toString();

    SchemaParser schemaParser = new SchemaParser();
    TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

    RuntimeWiring runtimeWiring = newRuntimeWiring()
      .type("Query", builder -> builder.dataFetcher("allLinks", this::getAllLinks))
      .build();

    SchemaGenerator schemaGenerator = new SchemaGenerator();
    GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema)
      .instrumentation(new JsonObjectAdapter())
      .build();
  }

  @Override
  protected Object getAllLinks(DataFetchingEnvironment env) {
    @SuppressWarnings("unchecked")
    List<Link> links = (List<Link>) super.getAllLinks(env);
    return links.stream()
      .map(link -> {
        JsonObject jsonObject = new JsonObject()
          .put("url", link.getUrl())
          .put("description", link.getDescription())
          .put("userId", link.getUserId());
        return jsonObject;
      })
      .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
  }

  @Test
  public void testSimpleGet() throws Exception {
    GraphQLRequest request = new GraphQLRequest()
      .setMethod(GET)
      .setGraphQLQuery("query { allLinks { url } }");
    request.send(client).onComplete(onSuccess(body -> {
      if (testData.checkLinkUrls(testData.urls(), body)) {
        testComplete();
      } else {
        fail(body.toString());
      }
    }));
    await();
  }

  @Test
  public void testSimplePost() throws Exception {
    GraphQLRequest request = new GraphQLRequest()
      .setGraphQLQuery("query { allLinks { url } }");
    request.send(client).onComplete(onSuccess(body -> {
      if (testData.checkLinkUrls(testData.urls(), body)) {
        testComplete();
      } else {
        fail(body.toString());
      }
    }));
    await();
  }
}

/*
 * Licensed to Mandrel under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Mandrel licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.mandrel.endpoints.contracts;

import io.mandrel.cluster.node.Node;
import io.mandrel.endpoints.rest.Apis;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(value = Apis.PREFIX + "/nodes", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
public interface NodesContract {

	@RequestMapping(method = RequestMethod.GET)
	public Map<URI, Node> all(@RequestHeader("target") URI target);

	@RequestMapping(params = "uri", method = RequestMethod.GET)
	public Optional<Node> id(@RequestParam URI uri, @RequestHeader("target") URI target);

}
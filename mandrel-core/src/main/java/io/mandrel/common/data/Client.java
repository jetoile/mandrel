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
package io.mandrel.common.data;

import io.mandrel.requests.Requester;
import io.mandrel.requests.Requester.RequesterDefinition;
import io.mandrel.requests.ftp.FtpRequester.FtpRequesterDefinition;
import io.mandrel.requests.http.ApacheHttpRequester.ApacheHttpRequesterDefinition;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class Client implements Serializable {

	private static final long serialVersionUID = -4242505953994309024L;

	@JsonProperty("requesters")
	private List<RequesterDefinition<? extends Strategy, ? extends Requester<?>>> requesters = Arrays.asList(new ApacheHttpRequesterDefinition(), new FtpRequesterDefinition());

}
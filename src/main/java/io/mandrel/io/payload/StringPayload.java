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
package io.mandrel.io.payload;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.google.common.base.Charsets;

/**
 * This implementation converts the String to a byte array using UTF-8 encoding.
 * If you wish to use a different encoding, please use {@link ByteArrayPayload}.
 * 
 */
public class StringPayload extends BasePayload<String> {

	private final byte[] bytes;

	// it is possible to discover length by walking the string and updating
	// current length based on
	// character code. However, this is process intense, and assumes an encoding
	// type of UTF-8
	public StringPayload(String content) {
		super(content);
		this.bytes = content.getBytes(Charsets.UTF_8);
		contentMetadata().contentLength(Long.valueOf(bytes.length));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InputStream openStream() {
		return new ByteArrayInputStream(bytes);
	}
}

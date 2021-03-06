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
package io.mandrel.transport.thrift.nifty;

import static com.google.common.base.Preconditions.checkNotNull;
import io.airlift.units.Duration;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import lombok.extern.slf4j.Slf4j;

import org.apache.thrift.TServiceClient;
import org.apache.thrift.transport.TTransportException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.local.DefaultLocalClientChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.Timer;

import com.facebook.nifty.client.NettyClientConfig;
import com.facebook.nifty.client.NiftyClientChannel;
import com.facebook.nifty.client.NiftyClientConnector;
import com.facebook.nifty.client.TNiftyClientChannelTransport;
import com.facebook.nifty.client.socks.Socks4ClientBootstrap;
import com.facebook.nifty.core.ShutdownUtil;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

@Slf4j
public class NiftyClient implements Closeable {
	public static final Duration DEFAULT_CONNECT_TIMEOUT = new Duration(20, TimeUnit.SECONDS);
	public static final Duration DEFAULT_RECEIVE_TIMEOUT = new Duration(30, TimeUnit.SECONDS);
	public static final Duration DEFAULT_READ_TIMEOUT = new Duration(30, TimeUnit.SECONDS);
	private static final Duration DEFAULT_SEND_TIMEOUT = new Duration(20, TimeUnit.SECONDS);

	private static final int DEFAULT_MAX_FRAME_SIZE = 16777216;

	private final NettyClientConfig nettyClientConfig;
	private final ExecutorService bossExecutor;
	private final ExecutorService workerExecutor;
	private final ChannelFactory channelFactory;
	private final HostAndPort defaultSocksProxyAddress;
	private final ChannelGroup allChannels = new DefaultChannelGroup();
	private final Timer timer;

	/**
	 * Creates a new NiftyClient with defaults: cachedThreadPool for
	 * bossExecutor and workerExecutor
	 */
	public NiftyClient() {
		this(NettyClientConfig.newBuilder().build(), false);
	}

	public NiftyClient(NettyClientConfig nettyClientConfig, boolean local) {
		this.nettyClientConfig = nettyClientConfig;
		if (local) {
			log.warn("Using local client");
			this.channelFactory = new DefaultLocalClientChannelFactory();
			this.timer = null;
			this.bossExecutor = null;
			this.workerExecutor = null;
			this.defaultSocksProxyAddress = null;
		} else {
			this.timer = nettyClientConfig.getTimer();
			this.bossExecutor = nettyClientConfig.getBossExecutor();
			this.workerExecutor = nettyClientConfig.getWorkerExecutor();
			this.defaultSocksProxyAddress = nettyClientConfig.getDefaultSocksProxyAddress();

			int bossThreadCount = nettyClientConfig.getBossThreadCount();
			int workerThreadCount = nettyClientConfig.getWorkerThreadCount();

			NioWorkerPool workerPool = new NioWorkerPool(workerExecutor, workerThreadCount, ThreadNameDeterminer.CURRENT);
			NioClientBossPool bossPool = new NioClientBossPool(bossExecutor, bossThreadCount, timer, ThreadNameDeterminer.CURRENT);

			this.channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
		}
	}

	public <T extends NiftyClientChannel> ListenableFuture<T> connectAsync(NiftyClientConnector<T> clientChannelConnector) {
		return connectAsync(clientChannelConnector, DEFAULT_CONNECT_TIMEOUT, DEFAULT_RECEIVE_TIMEOUT, DEFAULT_READ_TIMEOUT, DEFAULT_SEND_TIMEOUT,
				DEFAULT_MAX_FRAME_SIZE, defaultSocksProxyAddress);
	}

	public HostAndPort getDefaultSocksProxyAddress() {
		return defaultSocksProxyAddress;
	}

	public <T extends NiftyClientChannel> ListenableFuture<T> connectAsync(NiftyClientConnector<T> clientChannelConnector, @Nullable Duration connectTimeout,
			@Nullable Duration receiveTimeout, @Nullable Duration readTimeout, @Nullable Duration sendTimeout, int maxFrameSize) {
		return connectAsync(clientChannelConnector, connectTimeout, receiveTimeout, readTimeout, sendTimeout, maxFrameSize, defaultSocksProxyAddress);
	}

	public <T extends NiftyClientChannel> ListenableFuture<T> connectAsync(NiftyClientConnector<T> clientChannelConnector, @Nullable Duration connectTimeout,
			@Nullable Duration receiveTimeout, @Nullable Duration readTimeout, @Nullable Duration sendTimeout, int maxFrameSize,
			@Nullable HostAndPort socksProxyAddress) {
		checkNotNull(clientChannelConnector, "clientChannelConnector is null");

		ClientBootstrap bootstrap = createClientBootstrap(socksProxyAddress);
		bootstrap.setOptions(nettyClientConfig.getBootstrapOptions());

		if (connectTimeout != null) {
			bootstrap.setOption("connectTimeoutMillis", connectTimeout.toMillis());
		}

		bootstrap.setPipelineFactory(clientChannelConnector.newChannelPipelineFactory(maxFrameSize, nettyClientConfig));
		ChannelFuture nettyChannelFuture = clientChannelConnector.connect(bootstrap);
		nettyChannelFuture.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				Channel channel = future.getChannel();
				if (channel != null && channel.isOpen()) {
					allChannels.add(channel);
				}
			}
		});
		return new TNiftyFuture<>(clientChannelConnector, receiveTimeout, readTimeout, sendTimeout, nettyChannelFuture);
	}

	public <T extends NiftyClientChannel> TNiftyClientChannelTransport connectSync(Class<? extends TServiceClient> clientClass,
			NiftyClientConnector<T> clientChannelConnector) throws TTransportException, InterruptedException {
		return connectSync(clientClass, clientChannelConnector, DEFAULT_CONNECT_TIMEOUT, DEFAULT_RECEIVE_TIMEOUT, DEFAULT_READ_TIMEOUT, DEFAULT_SEND_TIMEOUT,
				DEFAULT_MAX_FRAME_SIZE, defaultSocksProxyAddress);
	}

	public <T extends NiftyClientChannel> TNiftyClientChannelTransport connectSync(Class<? extends TServiceClient> clientClass,
			NiftyClientConnector<T> clientChannelConnector, @Nullable Duration connectTimeout, @Nullable Duration receiveTimeout,
			@Nullable Duration readTimeout, @Nullable Duration sendTimeout, int maxFrameSize) throws TTransportException, InterruptedException {
		return connectSync(clientClass, clientChannelConnector, connectTimeout, receiveTimeout, readTimeout, sendTimeout, maxFrameSize, null);
	}

	public <T extends NiftyClientChannel> TNiftyClientChannelTransport connectSync(Class<? extends TServiceClient> clientClass,
			NiftyClientConnector<T> clientChannelConnector, @Nullable Duration connectTimeout, @Nullable Duration receiveTimeout,
			@Nullable Duration readTimeout, @Nullable Duration sendTimeout, int maxFrameSize, @Nullable HostAndPort socksProxyAddress)
			throws TTransportException, InterruptedException {
		try {
			T channel = connectAsync(clientChannelConnector, connectTimeout, receiveTimeout, readTimeout, sendTimeout, maxFrameSize, socksProxyAddress).get();
			return new TNiftyClientChannelTransport(clientClass, channel);
		} catch (ExecutionException e) {
			Throwables.propagateIfInstanceOf(e, TTransportException.class);
			throw new TTransportException(TTransportException.UNKNOWN, "Failed to establish client connection", e);
		}
	}

	@Override
	public void close() {
		// Stop the timer thread first, so no timeouts can fire during the rest
		// of the
		// shutdown process
		timer.stop();

		ShutdownUtil.shutdownChannelFactory(channelFactory, bossExecutor, workerExecutor, allChannels);
	}

	private ClientBootstrap createClientBootstrap(@Nullable HostAndPort socksProxyAddress) {
		if (socksProxyAddress != null) {
			return new Socks4ClientBootstrap(channelFactory, toInetAddress(socksProxyAddress));
		} else {
			return new ClientBootstrap(channelFactory);
		}
	}

	private static InetSocketAddress toInetAddress(HostAndPort hostAndPort) {
		return (hostAndPort == null) ? null : new InetSocketAddress(hostAndPort.getHostText(), hostAndPort.getPort());
	}

	private class TNiftyFuture<T extends NiftyClientChannel> extends AbstractFuture<T> {
		private TNiftyFuture(final NiftyClientConnector<T> clientChannelConnector, @Nullable final Duration receiveTimeout,
				@Nullable final Duration readTimeout, @Nullable final Duration sendTimeout, final ChannelFuture channelFuture) {
			channelFuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					try {
						if (future.isSuccess()) {
							Channel nettyChannel = future.getChannel();
							T channel = clientChannelConnector.newThriftClientChannel(nettyChannel, nettyClientConfig);
							channel.setReceiveTimeout(receiveTimeout);
							channel.setReadTimeout(readTimeout);
							channel.setSendTimeout(sendTimeout);
							set(channel);
						} else if (future.isCancelled()) {
							if (!cancel(true)) {
								setException(new TTransportException("Unable to cancel client channel connection"));
							}
						} else {
							throw future.getCause();
						}
					} catch (Throwable t) {
						setException(new TTransportException("Failed to connect client channel", t));
					}
				}
			});
		}
	}
}

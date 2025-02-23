package com.univocity.trader.iqfeed.api;

import io.netty.channel.*;
import org.asynchttpclient.*;

import java.time.*;

public abstract class HttpUtils {

	private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30);

	/**
	 * @param eventLoop
	 *
	 * @return new instance of AsyncHttpClient for EventLoop
	 */
	public static AsyncHttpClient newAsyncHttpClient(EventLoopGroup eventLoop, int maxFrameSize) {
		DefaultAsyncHttpClientConfig.Builder config = Dsl.config()
				.setEventLoopGroup(eventLoop)
				.addChannelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(DEFAULT_CONNECTION_TIMEOUT.toMillis()))
				.setWebSocketMaxFrameSize(maxFrameSize);
		return Dsl.asyncHttpClient(config);
	}
}
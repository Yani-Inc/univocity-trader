package com.univocity.trader.exchange.binance.api.client;

import io.netty.channel.*;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.*;
import org.asynchttpclient.proxy.ProxyServer;

import java.time.*;

public abstract class HttpUtils {

    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    /**
     * @param eventLoop
     * @return new instance of AsyncHttpClient for EventLoop
     */
    public static AsyncHttpClient newAsyncHttpClient(
            EventLoopGroup eventLoop, int maxFrameSize, ProxyServer proxyServer) {

        DefaultAsyncHttpClientConfig.Builder config = Dsl.config()
                .setEventLoopGroup(eventLoop)
                .addChannelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(DEFAULT_CONNECTION_TIMEOUT.toMillis()))
                .setWebSocketMaxFrameSize(maxFrameSize);

        // Setup proxy
        if (proxyServer != null) {
            config.setProxyServer(proxyServer);
        }
        return Dsl.asyncHttpClient(config);
    }
}
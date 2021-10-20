package com.univocity.trader.exchange.binance.api.client;

import com.univocity.trader.config.AccountConfiguration;
import io.netty.channel.*;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.*;
import org.asynchttpclient.proxy.ProxyServer;

import java.time.*;

public abstract class HttpUtils {

    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30);

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

    public static ProxyServer buildProxyServer(String ip, Integer port, String username, String password) {
        if (StringUtils.isNotBlank(ip) && port != null) {
            ProxyServer.Builder proxyServer = new ProxyServer.Builder(ip, port);

            // Setup authentication
            if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
                Realm realm = new Realm.Builder(username, password)
                        .setScheme(Realm.AuthScheme.BASIC)
                        .setUsePreemptiveAuth(true)
                        .setRealmName(username)
                        .build();
                proxyServer.setRealm(realm);
            }

            return proxyServer.build();
        }
        return null;
    }

    public static ProxyServer buildProxyServer(AccountConfiguration<?> accountConfiguration) {
        return buildProxyServer(
                accountConfiguration.proxyIp(),
                accountConfiguration.proxyPort(),
                accountConfiguration.proxyUsername(),
                accountConfiguration.proxyPassword());
    }
}
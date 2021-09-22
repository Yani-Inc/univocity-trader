package com.univocity.trader.exchange.binance;

import com.univocity.trader.exchange.binance.api.client.BinanceApiRestClient;
import com.univocity.trader.utils.ThreadName;

import java.util.Timer;
import java.util.TimerTask;

/** Maintain the data stream alive. */
public class KeepAliveUserDataStream {

    BinanceApiRestClient client;
    private String listenKey;
    private Timer timer;

    public KeepAliveUserDataStream(BinanceApiRestClient client){
        this.client = client;
    }
    
    public void start() {
        this.listenKey = client.startUserDataStream();
        final String threadName = ThreadName.generateNewName();
        TimerTask task = new TimerTask() {
            public void run() {
                Thread.currentThread().setName(threadName);
                client.ping();
                client.keepAliveUserDataStream(listenKey);
            }
        };
        timer = new Timer("Keep-alive Timer", true);
        long delay = 30000L; // this timeout is as recommended by Binance
        timer.scheduleAtFixedRate(task, delay, delay);
    }
}

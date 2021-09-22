package com.univocity.trader.exchange.binance;

import com.univocity.trader.exchange.binance.api.client.BinanceApiRestClient;
import com.univocity.trader.utils.ThreadName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/** Maintain the data stream alive. */
public class KeepAliveUserDataStream {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveUserDataStream.class);

    BinanceApiRestClient client;
    private String listenKey;
    private Timer timer;

    public KeepAliveUserDataStream(BinanceApiRestClient client){
        this.client = client;
    }
    
    public void start() {
        this.listenKey = client.startUserDataStream();
        final String threadName = ThreadName.generateNewName() + "-";
        TimerTask task = new TimerTask() {
            public void run() {
                try {
                    log.info("Start task KeepAliveUserDataStream");
                    Thread.currentThread().setName(threadName);
                    client.ping();
                    client.keepAliveUserDataStream(listenKey);
                } catch (Exception e) {
                    log.error("Exception during KeepAliveUserDataStream !", e);
                }
            }
        };
        timer = new Timer("Keep-alive Timer", true);
        long delay = 30000L; // this timeout is as recommended by Binance
        timer.scheduleAtFixedRate(task, delay, delay);
    }
}

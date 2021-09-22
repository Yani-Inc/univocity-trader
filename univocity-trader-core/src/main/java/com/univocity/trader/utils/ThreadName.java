package com.univocity.trader.utils;

import java.util.Map;
import java.util.TreeMap;

public class ThreadName {

    private final static Map<String, Integer> THREAD_NAMES = new TreeMap<>();

    public static synchronized String generateNewName() {
        String currentName = Thread.currentThread().getName().split("-")[0];
        Integer nb = THREAD_NAMES.get(currentName);
        nb = nb != null ? nb + 1 : 1;
        THREAD_NAMES.put(currentName, nb);
        return currentName + "-" + nb;
    }
}

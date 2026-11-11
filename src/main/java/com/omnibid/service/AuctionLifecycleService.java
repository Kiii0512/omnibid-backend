package com.omnibid.service;

public interface AuctionLifecycleService {

    void startScheduledAuctions();

    void closeEndedAuctions();
}


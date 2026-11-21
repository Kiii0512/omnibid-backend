package com.omnibid.service.impl;

import com.omnibid.common.BusinessException;
import com.omnibid.common.ErrorCode;
import com.omnibid.common.PageResponse;
import com.omnibid.dto.auction.AuctionResponse;
import com.omnibid.dto.watchlist.WatchlistSummaryResponse;
import com.omnibid.entity.Auction;
import com.omnibid.entity.Watchlist;
import com.omnibid.enums.AuctionStatus;
import com.omnibid.mapper.AuctionMapper;
import com.omnibid.repository.AuctionRepository;
import com.omnibid.repository.WatchlistRepository;
import com.omnibid.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistServiceImpl implements WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionMapper auctionMapper;

    @Override
    @Transactional
    public void addToWatchlist(Long userId, Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Auction not found with id: " + auctionId);
        }
        if (!watchlistRepository.existsByUserIdAndAuctionId(userId, auctionId)) {
            Watchlist watchlist = Watchlist.builder()
                    .userId(userId)
                    .auctionId(auctionId)
                    .build();
            watchlistRepository.save(watchlist);
        }
    }

    @Override
    @Transactional
    public void removeFromWatchlist(Long userId, Long auctionId) {
        watchlistRepository.deleteByUserIdAndAuctionId(userId, auctionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionResponse> getMyWatchlist(Long userId, Pageable pageable) {
        Page<Watchlist> page = watchlistRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<Long> auctionIds = page.getContent().stream().map(Watchlist::getAuctionId).toList();
        Map<Long, Auction> auctionMap = auctionRepository.findAllById(auctionIds).stream()
                .collect(Collectors.toMap(Auction::getId, a -> a, (a, b) -> a));

        List<AuctionResponse> responses = page.getContent().stream()
                .map(w -> auctionMap.get(w.getAuctionId()))
                .filter(Objects::nonNull)
                .map(a -> auctionMapper.toAuctionResponse(a, userId))
                .toList();

        return PageResponse.<AuctionResponse>builder()
                .content(responses)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public WatchlistSummaryResponse getWatchlistSummary(Long userId) {
        long total = watchlistRepository.countByUserId(userId);
        long active = 0;
        long scheduled = 0;
        long ended = 0;

        List<Object[]> rows = watchlistRepository.countByUserIdGroupByAuctionStatus(userId);
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] != null) {
                AuctionStatus status = (AuctionStatus) row[0];
                long count = ((Number) row[1]).longValue();
                if (status == AuctionStatus.ACTIVE) {
                    active = count;
                } else if (status == AuctionStatus.SCHEDULED) {
                    scheduled = count;
                } else if (status == AuctionStatus.ENDED) {
                    ended = count;
                }
            }
        }

        return new WatchlistSummaryResponse(total, active, scheduled, ended);
    }
}

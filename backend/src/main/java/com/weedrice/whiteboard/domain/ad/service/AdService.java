package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.entity.AdClickLog;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdService {

    private final AdRepository adRepository;
    private final AdClickLogRepository adClickLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AdResponse getAdResponse(String placement) {
        Ad ad = getAd(placement);
        if (ad == null) {
            return null;
        }
        return AdResponse.from(ad);
    }

    public Ad getAd(String placement) {
        LocalDateTime now = LocalDateTime.now(clock);
        long activeCount = adRepository.countActiveByPlacement(placement, now);
        if (activeCount == 0) {
            return null;
        }

        int randomOffset = ThreadLocalRandom.current().nextInt((int) Math.min(activeCount, Integer.MAX_VALUE));
        List<Ad> ads = adRepository.findActiveByPlacement(placement, now, PageRequest.of(randomOffset, 1));
        if (ads.isEmpty()) {
            ads = adRepository.findActiveByPlacement(placement, now, PageRequest.of(0, 1));
        }
        if (ads.isEmpty()) {
            return null;
        }
        return ads.get(0);
    }

    @Transactional
    public void recordAdImpression(Long adId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (adRepository.incrementImpressionCountForActive(adId, now) == 0) {
            throw new BusinessException(ErrorCode.AD_NOT_FOUND);
        }
    }

    @Transactional
    public String recordAdClick(Long adId, Long userId, String ipAddress) {
        LocalDateTime now = LocalDateTime.now(clock);
        Ad ad = adRepository.findActiveById(adId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.AD_NOT_FOUND));
        if (adRepository.incrementClickCountForActive(adId, now) == 0) {
            throw new BusinessException(ErrorCode.AD_NOT_FOUND);
        }

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        AdClickLog clickLog = AdClickLog.builder()
                .ad(ad)
                .user(user)
                .ipAddress(ipAddress)
                .clickedAt(now)
                .build();
        adClickLogRepository.save(clickLog);

        return ad.getTargetUrl();
    }
}

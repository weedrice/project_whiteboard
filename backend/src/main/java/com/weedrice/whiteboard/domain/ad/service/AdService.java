package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.entity.AdClickLog;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.ClientMetadataNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
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
        long activeAdCount = adRepository.countActiveByPlacement(placement, now);
        if (activeAdCount == 0) {
            return null;
        }
        Ad ad = findRandomActiveAd(placement, now, activeAdCount);
        if (ad != null) {
            return ad;
        }

        long refreshedActiveAdCount = adRepository.countActiveByPlacement(placement, now);
        if (refreshedActiveAdCount == 0) {
            return null;
        }
        return findRandomActiveAd(placement, now, refreshedActiveAdCount);
    }

    public String getActiveAdTargetUrl(Long adId) {
        return adRepository.findActiveById(adId, LocalDateTime.now(clock))
                .map(Ad::getTargetUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.AD_NOT_FOUND));
    }

    private Ad findRandomActiveAd(String placement, LocalDateTime now, long activeAdCount) {
        int candidateCount = activeAdCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) activeAdCount;
        int offset = ThreadLocalRandom.current().nextInt(candidateCount);
        return adRepository.findActiveByPlacement(placement, now, PageRequest.of(offset, 1))
                .stream()
                .findFirst()
                .orElse(null);
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

        AdClickUserResolution userResolution = resolveClickUser(userId);

        AdClickLog clickLog = AdClickLog.builder()
                .ad(ad)
                .user(userResolution.user())
                .ipAddress(ClientMetadataNormalizer.normalizeIpAddress(ipAddress))
                .anonymousReason(userResolution.anonymousReason())
                .clickedAt(now)
                .build();
        adClickLogRepository.save(clickLog);

        return ad.getTargetUrl();
    }

    private AdClickUserResolution resolveClickUser(Long userId) {
        if (userId == null) {
            return new AdClickUserResolution(null, AdClickLog.ANONYMOUS_REASON_ANONYMOUS_REQUEST);
        }
        return userRepository.findById(userId)
                .map(user -> user.isActiveAccount()
                        ? new AdClickUserResolution(user, null)
                        : new AdClickUserResolution(null, AdClickLog.ANONYMOUS_REASON_USER_NOT_ACTIVE))
                .orElseGet(() -> new AdClickUserResolution(null, AdClickLog.ANONYMOUS_REASON_USER_NOT_FOUND));
    }

    private record AdClickUserResolution(User user, String anonymousReason) {
    }
}

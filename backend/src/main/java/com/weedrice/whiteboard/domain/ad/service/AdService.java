package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.entity.AdClickLog;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdService {

    private final AdRepository adRepository;
    private final AdClickLogRepository adClickLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public Ad getAd(String placement) {
        LocalDateTime now = LocalDateTime.now();
        List<Ad> ads = adRepository.findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(placement, true, now, now);
        if (ads.isEmpty()) {
            return null;
        }
        // TODO: 여러 광고 중 하나를 선택하는 로직 (예: 랜덤, 가중치 등)
        Ad ad = ads.get(0);
        ad.incrementImpressionCount();
        return ad;
    }

    @Transactional
    public String recordAdClick(Long adId, Long userId, String ipAddress) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AD_NOT_FOUND));
        ad.incrementClickCount();

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        AdClickLog clickLog = AdClickLog.builder()
                .ad(ad)
                .user(user)
                .ipAddress(ipAddress)
                .build();
        adClickLogRepository.save(clickLog);

        return ad.getTargetUrl();
    }
}

package com.weedrice.whiteboard.global.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {

    public static final ZoneId KST_ZONE_ID = ZoneId.of("Asia/Seoul");
    public static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 현재 KST (한국 표준시)의 LocalDateTime을 반환합니다.
     *
     * @return 현재 KST의 LocalDateTime
     */
    public static LocalDateTime nowKST() {
        return ZonedDateTime.now(KST_ZONE_ID).toLocalDateTime();
    }

    /**
     * LocalDateTime을 KST ZonedDateTime으로 변환합니다.
     *
     * @param localDateTime 변환할 LocalDateTime
     * @return KST ZonedDateTime
     */
    public static ZonedDateTime toKSTZonedDateTime(LocalDateTime localDateTime) {
        return localDateTime.atZone(KST_ZONE_ID);
    }

    /**
     * KST ZonedDateTime을 LocalDateTime으로 변환합니다.
     *
     * @param zonedDateTime 변환할 ZonedDateTime
     * @return LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(ZonedDateTime zonedDateTime) {
        return zonedDateTime.withZoneSameInstant(KST_ZONE_ID).toLocalDateTime();
    }

    /**
     * LocalDateTime을 기본 포맷 (yyyy-MM-dd HH:mm:ss)의 문자열로 포맷팅합니다.
     *
     * @param localDateTime 포맷팅할 LocalDateTime
     * @return 포맷팅된 문자열
     */
    public static String format(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.format(DEFAULT_DATE_TIME_FORMATTER);
    }

    /**
     * LocalDateTime을 지정된 포맷의 문자열로 포맷팅합니다.
     *
     * @param localDateTime 포맷팅할 LocalDateTime
     * @param pattern       날짜/시간 포맷 패턴 (예: "yyyy/MM/dd HH:mm:ss")
     * @return 포맷팅된 문자열
     */
    public static String format(LocalDateTime localDateTime, String pattern) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 문자열을 기본 포맷 (yyyy-MM-dd HH:mm:ss)의 LocalDateTime으로 파싱합니다.
     *
     * @param dateTimeString 파싱할 문자열
     * @return 파싱된 LocalDateTime
     */
    public static LocalDateTime parse(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString, DEFAULT_DATE_TIME_FORMATTER);
    }

    /**
     * 문자열을 지정된 포맷의 LocalDateTime으로 파싱합니다.
     *
     * @param dateTimeString 파싱할 문자열
     * @param pattern        날짜/시간 포맷 패턴
     * @return 파싱된 LocalDateTime
     */
    public static LocalDateTime parse(String dateTimeString, String pattern) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern(pattern));
    }
}

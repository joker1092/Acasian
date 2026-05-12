package com.acasian.iot.Calendar.controller;

import com.acasian.iot.Calendar.model.CalendarDate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class CalendarController {
    /**
     * 특정 연/월에 해당하는 달력 날짜 리스트(42개)를 반환합니다.
     */
    public List<CalendarDate> getDatesInMonth(YearMonth yearMonth) {
        List<CalendarDate> dates = new ArrayList<>();
        LocalDate firstOfMonth = yearMonth.atDay(1);
        
        // 해당 월의 1일이 무슨 요일인지 계산 (0:일요일 ~ 6:토요일)
        int dayOfWeekOfFirst = firstOfMonth.getDayOfWeek().getValue() % 7; 
        
        // 달력의 시작 날짜 (이전 달의 일부 날짜 포함)
        LocalDate startDate = firstOfMonth.minusDays(dayOfWeekOfFirst);
        
        // 항상 6주(42일) 분량의 데이터를 생성하여 달력 높이를 일정하게 유지
        for (int i = 0; i < 42; i++) {
            LocalDate date = startDate.plusDays(i);
            dates.add(new CalendarDate(date, YearMonth.from(date).equals(yearMonth)));
        }
        return dates;
    }
}

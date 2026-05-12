package com.acasian.iot.Calendar.model;

import java.time.LocalDate;

public class CalendarDate {
    private final LocalDate date;
    private final boolean isCurrentMonth;
    private boolean isSelected;
    private final boolean isToday;

    public CalendarDate(LocalDate date, boolean isCurrentMonth) {
        this.date = date;
        this.isCurrentMonth = isCurrentMonth;
        this.isToday = date.isEqual(LocalDate.now());
    }

    public LocalDate getDate() { return date; }
    public boolean isCurrentMonth() { return isCurrentMonth; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    public boolean isToday() { return isToday; }
    
    public int getDayOfMonth() { return date.getDayOfMonth(); }
}

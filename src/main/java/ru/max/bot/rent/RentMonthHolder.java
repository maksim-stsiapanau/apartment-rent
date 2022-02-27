package ru.max.bot.rent;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class RentMonthHolder {

    private Map<String, Counter> light;
    private Map<Integer, Counter> coldWater;
    private Map<Integer, Counter> hotWater;
    private Counter outfall;
    private Double takeout;
    private String takeoutDesc;
    private String month;
    private Double totalAmount;
    private Double rentAmount;
    private LastIndicationsHolder lastIndications;
    private String chatId;
    private String owner;

    public RentMonthHolder(String chatId, String owner) {
        this.chatId = chatId;
        this.owner = owner;
    }

    public Map<String, Counter> getLight() {
        if (null == this.light) {
            this.light = new LinkedHashMap<>();
        }
        return this.light;
    }

    public Map<Integer, Counter> getColdWater() {
        if (null == this.coldWater) {
            this.coldWater = new LinkedHashMap<>();
        }
        return this.coldWater;
    }

    public Map<Integer, Counter> getHotWater() {
        if (null == this.hotWater) {
            this.hotWater = new LinkedHashMap<>();
        }
        return this.hotWater;
    }


    public Double getTotalAmount() {
        this.totalAmount = 0.0;

        this.light.forEach((key, value) -> this.totalAmount += value.getPrice());

        this.coldWater.forEach((key, value) -> this.totalAmount += value.getPrice());

        this.hotWater.forEach((key, value) -> this.totalAmount += value.getPrice());

        if (null != this.outfall) {
            this.totalAmount += this.outfall.getPrice();
        }

        this.totalAmount += this.rentAmount;

        if (null != this.takeout) {
            this.totalAmount -= this.takeout;
        }

        return this.totalAmount;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.SHORT_PREFIX_STYLE);
    }


}

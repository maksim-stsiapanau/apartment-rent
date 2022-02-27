package ru.max.bot.rent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.TreeMap;

/**
 * this.objectMapper.configure(
 * DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
@JsonIgnoreProperties({"typeOfWater", "typeOfRates", "waterSet", "ratesSet",
        "waitValue", "setWaterIndications", "setRates"})
@NoArgsConstructor
public class PrimaryWaterHolder {

    private Integer countHotWaterCounter;
    private Integer countColdWaterCounter;
    private String typeOfWater;
    private String typeOfRates;
    private boolean waterSet;
    private boolean ratesSet;
    private boolean waitValue;
    private Double hotWaterRate;
    private Double coldWaterRate;
    private Double outfallRate;
    private Map<Integer, WaterHolder> hotWater;
    private Map<Integer, WaterHolder> coldWater;

    public void setHotWater() {
        if (null == this.hotWater) {
            this.hotWater = new TreeMap<>();
        }
    }

    public void setColdWater() {
        if (null == this.coldWater) {
            this.coldWater = new TreeMap<>();
        }
    }

    public boolean isSetRates() {
        return this.hotWaterRate != null && this.coldWaterRate != null && this.outfallRate != null;
    }

    public boolean isSetWaterIndications() {
        return this.coldWater != null && this.hotWater != null
                && this.coldWater.size() == this.countColdWaterCounter && this.hotWater
                .size() == this.countHotWaterCounter;
    }
}

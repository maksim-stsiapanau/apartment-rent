package ru.max.bot.rent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.TreeMap;

/**
 * Hold information about primary counters of light
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
@JsonIgnoreProperties({"setRates", "setIndications"})
public class PrimaryLightHolder {

    private Integer tariffType;
    private String period;

    /**
     * key - name value - indicator
     */
    private final Map<String, Double> indications = new TreeMap<>();

    /**
     * key - name value - tariff price
     */
    private Map<String, Double> rates;

    public PrimaryLightHolder() {

    }


    public boolean isSetIndications() {
        return this.indications.size() == this.tariffType;
    }

    public boolean isSetRates() {
        return this.rates.size() == this.tariffType;
    }


    public void initRates() {
        if (null == this.rates) {
            this.rates = new TreeMap<>();
        }
    }

    public enum Periods {
        DAY, NIGHT, PEAK, HALF_PEAK
    }

    public static enum PeriodsRus {
        ДЕНЬ, НОЧЬ, ПИК, ПОЛУПИК
    }
}

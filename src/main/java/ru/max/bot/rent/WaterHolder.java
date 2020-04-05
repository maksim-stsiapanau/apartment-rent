package ru.max.bot.rent;

import lombok.Data;

/**
 * Hold information about counter of water
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
public class WaterHolder {

    private String typeOfWater;
    private String alias;
    private Double primaryIndication;

    public WaterHolder(String typeOfWater) {
        this.typeOfWater = typeOfWater;
        this.alias = typeOfWater;

    }


    public WaterHolder() {
    }


}

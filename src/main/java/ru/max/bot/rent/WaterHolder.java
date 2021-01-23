package ru.max.bot.rent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hold information about counter of water
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WaterHolder {

    private String typeOfWater;
    private String alias;
    private Double primaryIndication;

    public WaterHolder(String typeOfWater) {
        this.typeOfWater = typeOfWater;
        this.alias = typeOfWater;
    }
}

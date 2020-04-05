package ru.max.bot.holders;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.max.bot.rent.PrimaryLightHolder;
import ru.max.bot.rent.PrimaryWaterHolder;

@Data
@AllArgsConstructor
public class RatesHolder {

    private PrimaryLightHolder light;
    private PrimaryWaterHolder water;


}

package ru.max.bot.rent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Contains last indicators of lights and water. Used for calculating total rent
 * by month
 *
 * @author Maksim Stepanov
 * @email maksim.stsiapanau@gmail.com
 */
@Data
public class LastIndicationsHolder {

    private Map<String, Double> light;
    private Map<Integer, WaterHolder> hotWater;
    private Map<Integer, WaterHolder> coldWater;

    public LastIndicationsHolder() {

    }


    public <T, V> Map<T, V> copyMap(Map<T, V> map) {
        return map
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(Entry::getKey, Entry::getValue, (e1,
                                                                          e2) -> e1, TreeMap::new));

    }

    public <T> List<T> copyList(List<T> list) {
        return new ArrayList<>(list);
    }

}

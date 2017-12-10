package com.gotofinal.darkrise.crafting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class LevelFunction
{
    private static Map<Integer, Double> map = new HashMap<>();

    public static double getXP(int level)
    {
        return map.get(level);
    }

    public static int getLevel(double xp)
    {
        Optional<Map.Entry<Integer, Double>> val = map.entrySet()
                .stream()
                .filter(e -> e.getValue() < xp)
                .reduce((i, d) -> d);

        return val.isPresent() ? val.get().getKey() : 0;
    }

    public static void generate(int levels)
    {
        map.clear();

        for (int level = 1; level <= levels; level++)
        {
            double xp = 0;

            for (int n = 1; n < level; n++)
            {
                xp += Math.floor(n + 300 * Math.pow(2, n / 7));
            }

            xp = Math.floor(xp);
            xp /= 4;

            map.put(level, xp);
        }
    }
}
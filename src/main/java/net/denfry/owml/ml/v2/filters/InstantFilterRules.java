package net.denfry.owml.ml.v2.filters;

import org.bukkit.entity.Player;

/**
 * TIER 1: INSTANT FILTER
 * Синхронный, работает менее 1 миллисекунды на main thread.
 * Только детерминированные Java-правила без ML.
 */
public class InstantFilterRules {

    /**
     * Оценивает действия майнинга.
     * Возвращает количество очков подозрения, которые нужно добавить.
     */
    public static int evaluateXray(boolean decoyTouched, double lookDeviationDegrees, 
                                   int visibleOresBypassed, double oreToStoneRatio, double serverNormRatio) {
        int points = 0;

        // decoy_ore_touched дает немедленный флаг высокой уверенности (перехватывается в другом месте, но здесь тоже можно дать макс)
        if (decoyTouched) {
            points += 100; 
        }

        // Игрок копал строго по вектору к скрытой руде с отклонением менее 15 градусов
        if (lookDeviationDegrees < 15.0) {
            points += 40;
        }

        // Игрок прошёл мимо трёх и более видимых руд но нашёл скрытые
        if (visibleOresBypassed >= 3) {
            points += 30;
        }

        // Отношение найденной руды к сломанному камню за сессию превышает 15% при норме 2%
        if (oreToStoneRatio > 0.15 && serverNormRatio < 0.05) {
            points += 35;
        }

        return points;
    }

    /**
     * Оценивает боевые действия и движения.
     * Возвращает количество очков подозрения.
     */
    public static int evaluateCombat(double yawChange, double distanceToTarget, double maxReach, 
                                     int ping, int ticksInAir, boolean allHeadshots, double headshotDeviation,
                                     double speed, double maxAllowedSpeed) {
        int points = 0;

        // угол поворота головы более 180 градусов в одном тике физически невозможен
        if (Math.abs(yawChange) > 180.0) {
            points += 100; // Immediate flag
        }

        // Игрок наносит урон цели находящейся дальше максимального reach + 0.5 с учётом ping
        double pingCompensatedReach = maxReach + 0.5 + (ping * 0.005);
        if (distanceToTarget > pingCompensatedReach) {
            points += 45;
        }

        // Игрок в воздухе более 20 тиков без прыжка и без эффектов
        if (ticksInAir > 20) {
            points += 50;
        }

        // Все удары в течение 10 секунд попадают в голову с отклонением менее 2 градусов
        if (allHeadshots && headshotDeviation < 2.0) {
            points += 40;
        }

        // Скорость движения превышает максимальную
        if (speed > maxAllowedSpeed) {
            points += 35;
        }

        return points;
    }
}

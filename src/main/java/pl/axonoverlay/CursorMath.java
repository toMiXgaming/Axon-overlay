package pl.axonoverlay;

/**
 * Zamiana obrotu głowy gracza na pozycję kursora w pikselach płótna.
 *
 * <p>Shader mierzy kursor od yaw 0 / pitch 0, więc sesja ustawia widok gracza dokładnie tam przy
 * otwarciu. Tutaj liczymy tylko matematykę — żadnego Bukkita, więc testy są natychmiastowe.</p>
 */
public final class CursorMath {

    private CursorMath() {
    }

    /**
     * Kursor w pikselach płótna.
     *
     * @param yaw   obrót poziomy w stopniach (dowolna wartość, normalizowana do [-180, 180))
     * @param pitch obrót pionowy w stopniach (-90 = patrzysz w górę, 90 = w dół)
     * @return {x, y} w granicach [0, WIDTH] × [0, HEIGHT]
     */
    public static double[] cursor(double yaw, double pitch) {
        // Przycinamy, nie zawijamy: gdy gracz obróci głowę dalej niż pół zakresu,
        // kursor staje na krawędzi i rusza natychmiast po zawróceniu myszą.
        double u = clamp01(0.5 + clampYaw(yaw) / OverlayLayout.CURSOR_YAW_RANGE);
        double v = clamp01(0.5 + clampPitch(pitch) / OverlayLayout.CURSOR_PITCH_RANGE);
        return new double[]{u * OverlayLayout.WIDTH, v * OverlayLayout.HEIGHT};
    }

    /**
     * Normalizuje yaw do zakresu [-180, 180). Bukkit potrafi podać 350 albo -400 — po normalizacji
     * kursor nie przeskakuje na drugi koniec ekranu.
     */
    public static double normalizeYaw(double yaw) {
        double wrapped = yaw % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        } else if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    /**
     * Najbliższy dopuszczalny yaw, gdy kursor doszedł do krawędzi. Głowa kręci się dalej, ale
     * kursor stoi — po zawróceniu myszą musi ruszyć natychmiast, więc przycinamy widok.
     */
    public static double clampYaw(double yaw) {
        double max = OverlayLayout.CURSOR_YAW_RANGE / 2.0;
        return Math.max(-max, Math.min(max, normalizeYaw(yaw)));
    }

    /** To samo dla pionu. */
    public static double clampPitch(double pitch) {
        double max = OverlayLayout.CURSOR_PITCH_RANGE / 2.0;
        return Math.max(-max, Math.min(max, pitch));
    }

    /** Czy kursor wyszedł poza dopuszczalny obrót (z tolerancją na drganie myszy). */
    public static boolean outside(double yaw, double pitch, double tolerance) {
        double maxYaw = OverlayLayout.CURSOR_YAW_RANGE / 2.0;
        double maxPitch = OverlayLayout.CURSOR_PITCH_RANGE / 2.0;
        return Math.abs(normalizeYaw(yaw)) > maxYaw + tolerance
                || Math.abs(pitch) > maxPitch + tolerance;
    }

    /** Piksel płótna → współrzędna świata, przy danym środku i skali (bloki na piksel). */
    public static double[] toWorld(double pixelX, double pixelY, double centerX, double centerZ, int scale) {
        double worldX = centerX + (pixelX - OverlayLayout.WIDTH / 2.0) * scale;
        double worldZ = centerZ + (pixelY - OverlayLayout.HEIGHT / 2.0) * scale;
        return new double[]{worldX, worldZ};
    }

    /** Współrzędna świata → piksel płótna (odwrotność {@link #toWorld}). */
    public static double[] toPixel(double worldX, double worldZ, double centerX, double centerZ, int scale) {
        double pixelX = (worldX - centerX) / scale + OverlayLayout.WIDTH / 2.0;
        double pixelY = (worldZ - centerZ) / scale + OverlayLayout.HEIGHT / 2.0;
        return new double[]{pixelX, pixelY};
    }

    /** Czy punkt leży w prostokącie płótna (kliknięcia poza ekranem ignorujemy). */
    public static boolean inside(double pixelX, double pixelY) {
        return pixelX >= 0 && pixelX < OverlayLayout.WIDTH
                && pixelY >= 0 && pixelY < OverlayLayout.HEIGHT;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

package pl.axonoverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Wylicza najmniejsze prostokąty, które zmieniły się na kaflu 128×128.
 *
 * <p>Zamiast wysyłać cały kafel (16 KB) przy każdej zmianie, sesja wysyła tylko te fragmenty:
 * zmienione wiersze są łączone w pasy (przerwa do {@link #MERGE_GAP} wierszy nie rozdziela pasa),
 * a każdy pas ma własny zakres w poziomie. Tabela sprite'ów w ostatnim wierszu i ruchomy znacznik
 * w środku kafla to wtedy dwa małe pakiety, a nie jeden prostokąt przez cały kafel.</p>
 *
 * <p>Klasa jest czysta (bez Bukkita), więc testy liczą ją bez serwera.</p>
 */
final class TilePatches {

    static final int MERGE_GAP = 6;

    private TilePatches() {
    }

    /**
     * @return lista {x, y, szerokość, wysokość}; pusta, gdy nic się nie zmieniło
     */
    static List<int[]> changed(byte[] before, byte[] after, int size) {
        List<int[]> result = new ArrayList<>();
        int runStart = -1;
        int runEnd = -1;
        int minX = size;
        int maxX = -1;
        int gap = 0;
        for (int y = 0; y < size; y++) {
            int rowMin = size;
            int rowMax = -1;
            int row = y * size;
            for (int x = 0; x < size; x++) {
                if (before[row + x] != after[row + x]) {
                    if (x < rowMin) rowMin = x;
                    rowMax = x;
                }
            }
            if (rowMax >= 0) {
                if (runStart < 0) {
                    runStart = y;
                }
                runEnd = y;
                minX = Math.min(minX, rowMin);
                maxX = Math.max(maxX, rowMax);
                gap = 0;
            } else if (runStart >= 0 && ++gap > MERGE_GAP) {
                result.add(new int[]{minX, runStart, maxX - minX + 1, runEnd - runStart + 1});
                runStart = -1;
                minX = size;
                maxX = -1;
                gap = 0;
            }
        }
        if (runStart >= 0) {
            result.add(new int[]{minX, runStart, maxX - minX + 1, runEnd - runStart + 1});
        }
        return result;
    }

    /** Łączna liczba pikseli w prostokątach. */
    static int area(List<int[]> rects) {
        int total = 0;
        for (int[] r : rects) {
            total += r[2] * r[3];
        }
        return total;
    }
}

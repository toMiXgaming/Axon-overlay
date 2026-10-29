# AxonOverlay

Wspólna biblioteka pełnoekranowych nakładek dla pluginów AxonCore
(AxonMap, Axon-items). Rysuje ekran 640×384 px z 15 kafli mapy rozpiętych na
ścianach komórki kamery, z kursorem sterowanym obrotem głowy i przechwytywaniem
kliknięć.

## Jak korzystać

Biblioteka nie jest zależnością kompilacyjną. Pobiera się ją w runtime:

```java
OverlayApi api = Bukkit.getServicesManager().load(OverlayApi.class);
if (api == null || !api.ready()) {
    return; // brak AxonOverlay albo brak PacketEvents — pełny ekran niedostępny
}

OverlayAvailability.Reason reason = api.availability(player);
if (!reason.available()) {
    player.sendMessage("Nie teraz: " + reason.key());
    return;
}

OverlaySession session = api.open(player, OverlaySpec.builder("warsztat")
        .hideHud(true)
        .lockMovement(true)
        .initialScale(1)
        .tilesPerSecond(10)
        .restorePosition(true)
        .build());

session.onInput(new InputListener() {
    @Override
    public void onCursor(int x, int y) {
        // kursor w pikselach canvasu
    }

    @Override
    public void onLeftClick(boolean down, int x, int y) {
        // kliknięcie lewym przyciskiem
    }

    @Override
    public void onClose() {
        // gracz kucnął albo wywołano close()
    }
});

// rysowanie
OverlayCanvas canvas = session.canvas();
canvas.fillRect(0, 0, OverlayLayout.WIDTH, OverlayLayout.HEIGHT, (byte) 0);
canvas.markAllDirty();
```

## Układ

| Stała | Wartość |
|---|---|
| `COLS` × `ROWS` | 5 × 3 |
| `TILE` | 128 px |
| `WIDTH` × `HEIGHT` | 640 × 384 px |
| `CURSOR_YAW_RANGE` | 180° |
| `CURSOR_PITCH_RANGE` | 100° |

## Budowanie

```
gradlew.bat build
```

Wynik: `build/libs/Axon-overlay-0.1.0.jar`. Wymaga Paper 1.21.4+ i PacketEvents 2.7.0.

## Testy

```
gradlew.bat test
```

33 testy jednostkowe warstwy czystej logiki (`CursorMath`, `DirtyTiles`) —
bez stawiania serwera, w milisekundach.

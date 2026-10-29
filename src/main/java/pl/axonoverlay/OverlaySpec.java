package pl.axonoverlay;

/**
 * Opis nakładki przekazywany do {@link OverlayApi#open(org.bukkit.entity.Player, OverlaySpec)}.
 *
 * <p>Spec decyduje o tym, jak biblioteka przygotowuje ekran: czy blokuje ruch,
 * czy pozwala graczowi chodzić, jaki jest początkowy zoom i czy nakładka ma
 * przejąć ukrywanie HUD. Sam obraz rysuje nakładka przez {@link OverlayCanvas}.</p>
 */
public final class OverlaySpec {

    private final String id;
    private final boolean lockMovement;
    private final boolean hideHud;
    private final int initialScale;
    private final int tilesPerSecond;
    private final boolean restorePosition;

    private OverlaySpec(Builder builder) {
        this.id = builder.id;
        this.lockMovement = builder.lockMovement;
        this.hideHud = builder.hideHud;
        this.initialScale = builder.initialScale;
        this.tilesPerSecond = builder.tilesPerSecond;
        this.restorePosition = builder.restorePosition;
    }

    /** Nowy builder z domyślnymi wartościami: ruch zablokowany, HUD ukryty, zoom 1. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Identyfikator nakładki, np. {@code "axonmap"} albo {@code "warsztat"}. */
    public String id() {
        return id;
    }

    /** Czy gracz ma stać w miejscu (fotel) zamiast chodzić. */
    public boolean lockMovement() {
        return lockMovement;
    }

    /** Czy biblioteka ma ukrywać pasek szybkiego wyboru i trzymany przedmiot. */
    public boolean hideHud() {
        return hideHud;
    }

    /** Początkowy zoom mapy w blokach na piksel. */
    public int initialScale() {
        return initialScale;
    }

    /** Limit kafli wysyłanych na sekundę dla tej sesji. */
    public int tilesPerSecond() {
        return tilesPerSecond;
    }

    /** Czy po zamknięciu przywrócić graczowi pozycję sprzed otwarcia. */
    public boolean restorePosition() {
        return restorePosition;
    }

    /** Budowniczy specyfikacji. */
    public static final class Builder {

        private final String id;
        private boolean lockMovement = true;
        private boolean hideHud = true;
        private int initialScale = 1;
        private int tilesPerSecond = DirtyTiles.DEFAULT_TILES_PER_SECOND;
        private boolean restorePosition = true;

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Nakładka musi mieć niepuste id");
            }
            this.id = id;
        }

        public Builder lockMovement(boolean value) {
            this.lockMovement = value;
            return this;
        }

        public Builder hideHud(boolean value) {
            this.hideHud = value;
            return this;
        }

        public Builder initialScale(int value) {
            this.initialScale = Math.max(1, value);
            return this;
        }

        public Builder tilesPerSecond(int value) {
            this.tilesPerSecond = Math.max(1, value);
            return this;
        }

        public Builder restorePosition(boolean value) {
            this.restorePosition = value;
            return this;
        }

        public OverlaySpec build() {
            return new OverlaySpec(this);
        }
    }
}

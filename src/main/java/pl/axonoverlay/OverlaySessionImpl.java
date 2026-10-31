package pl.axonoverlay;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementacja {@link OverlaySession}: fotel, fałszywe ramki, pętla ticków i wysyłka kafli.
 *
 * <p>Port z AxonMap. Sesja trzyma własny bufor pikseli, wysyła najwyżej
 * {@link DirtyTiles#tilesPerSecond()} kafli na sekundę i przywraca gracza na
 * miejsce po zamknięciu.</p>
 *
 * <p>Każdy kafel to osobna, trwała {@link MapView} z własnym
 * {@link OverlayMapRenderer}. Dzięki temu wysyłka to zwykłe
 * {@link Player#sendMap(MapView)} — bez ręcznego budowania pakietów mapy,
 * które różnią się między wersjami protokołu.</p>
 */
final class OverlaySessionImpl implements OverlaySession {

    /** Co ile ticków odświeżamy kursor i wejście. */
    private static final int TICK_PERIOD = 2;

    /** Maksymalna odległość od fotela, przy której przywracamy pozycję. */
    private static final double RESTORE_DISTANCE = 4.0;

    private final Plugin plugin;
    private final Player player;
    private final java.util.UUID playerId;
    private final OverlaySpec spec;
    private final OverlayCanvas canvas;
    private final DirtyTiles dirty;
    private final OverlayPackets.State packetState;
    private final int[] entityIds = new int[OverlayLayout.TILES * 2 + 4];
    private final Location origin;
    private final String worldName;
    private final List<MapView> views = new ArrayList<>(OverlayLayout.TILES);
    private final List<ItemStack> mapItems = new ArrayList<>(OverlayLayout.TILES);

    private ArmorStand seat;
    private Runnable onClosed = () -> {
    };
    private InputListener listener = new InputListener() {
    };
    private volatile boolean open = true;
    private boolean framesSpawned;
    private int scale;
    private double centerX;
    private double centerZ;
    private int taskId = -1;
    private boolean lastForward;
    private boolean lastBack;
    private boolean lastLeft;
    private boolean lastRight;
    private boolean lastJump;
    private boolean lastSneak;
    private int lastCursorX = -1;
    private int lastCursorY = -1;

    OverlaySessionImpl(Plugin plugin, Player player, OverlaySpec spec) {
        this.plugin = plugin;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.spec = spec;
        this.dirty = new DirtyTiles(spec.tilesPerSecond());
        this.canvas = new OverlayCanvas(dirty);
        this.origin = player.getLocation().clone();
        this.worldName = player.getWorld().getName();
        this.scale = spec.initialScale();
        this.centerX = origin.getX();
        this.centerZ = origin.getZ();
        this.packetState = new OverlayPackets.State(player.getEntityId(), entityIds, spec.hideHud());
        for (int i = 0; i < entityIds.length; i++) {
            entityIds[i] = Integer.MAX_VALUE - 1000 - i;
        }
        prepareMaps();
    }

    /**
     * Tworzy trwałe widoki mapy i przedmioty ramek.
     *
     * <p>Widoki są tworzone raz na sesję i trzymane do zamknięcia. Renderer
     * kopiuje z aktualnego bufora pikseli, więc każda wysyłka pokazuje świeży
     * obraz bez ponownego budowania pakietów.</p>
     */
    private void prepareMaps() {
        World world = player.getWorld();
        for (int tile = 0; tile < OverlayLayout.TILES; tile++) {
            MapView view = Bukkit.createMap(world);
            for (var renderer : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.addRenderer(new OverlayMapRenderer(this, tile));
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            views.add(view);

            ItemStack item = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta != null) {
                meta.setMapView(view);
                item.setItemMeta(meta);
            }
            mapItems.add(item);
        }
    }

    /** Menedżer podpina tu sprzątanie, żeby zamknięcie z dowolnego miejsca zwalniało gracza. */
    void onClosed(Runnable callback) {
        this.onClosed = callback;
    }

    /** Bufor pikseli widziany przez renderer mapy. */
    byte[] pixels() {
        return canvas.pixels();
    }

    /** Stan widziany przez nasłuch pakietów. */
    OverlayPackets.State packetState() {
        return packetState;
    }

    /** Identyfikatory encji ramek (do rejestracji w nasłuchu). */
    int[] entityIds() {
        return entityIds;
    }

    /** Wywoływane przez nasłuch po kliknięciu w ramkę. */
    void handleClick(boolean right) {
        if (!open) {
            return;
        }
        double[] cursor = CursorMath.cursor(player.getLocation().getYaw(), player.getLocation().getPitch());
        int x = (int) Math.round(cursor[0]);
        int y = (int) Math.round(cursor[1]);
        if (right) {
            listener.onRightClick(true, x, y);
        } else {
            listener.onLeftClick(true, x, y);
        }
    }

    /** Uruchamia sesję: fotel, teleport, ramki, pętla. */
    void start() {
        World world = player.getWorld();
        Location seatLocation = origin.clone();
        seatLocation.setYaw(0f);
        seatLocation.setPitch(0f);
        seat = world.spawn(seatLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
        });
        // Inny plugin (limity spawnu, ochrona regionu) może anulować spawn lub wsadzenie.
        if (!seat.isValid() || !seat.addPassenger(player)) {
            if (seat.isValid()) {
                seat.remove();
            }
            open = false;
            return;
        }
        player.teleport(seatLocation);
        if (spec.hideHud()) {
            ClientView.hideHand(player);
            ClientView.sendTime(player);
        }
        // Ramki spawnujemy po dwóch tickach, żeby klient zdążył przetworzyć teleport.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (open) {
                spawnFrames();
                sendMaps();
            }
        }, 2L);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD).getTaskId();
    }

    private void spawnFrames() {
        if (framesSpawned) {
            return;
        }
        framesSpawned = true;
        Location eye = player.getEyeLocation();
        int x = eye.getBlockX();
        int y = eye.getBlockY();
        int z = eye.getBlockZ();
        int index = 0;
        // Dwie ramki na kafel: północ i południe, żeby obraz był widoczny z obu stron.
        for (int tile = 0; tile < OverlayLayout.TILES; tile++) {
            FakeFrames.spawn(player, entityIds[index++], x, y, z, FakeFrames.FACING_NORTH, mapItems.get(tile));
            FakeFrames.spawn(player, entityIds[index++], x, y, z, FakeFrames.FACING_SOUTH, mapItems.get(tile));
        }
        // Cztery ściany zamykające widok — pokazują kafel 0, więc kursor zawsze trafia w ramkę.
        int[] walls = {FakeFrames.FACING_WEST, FakeFrames.FACING_EAST, FakeFrames.FACING_UP, FakeFrames.FACING_DOWN};
        for (int i = 0; i < walls.length; i++) {
            FakeFrames.spawn(player, entityIds[index++], x, y, z, walls[i], mapItems.get(0));
        }
        packetState.handHidden = true;
        if (spec.hideHud()) {
            ClientView.hideHand(player);
            ClientView.sendTime(player);
        }
    }

    private void sendMaps() {
        for (MapView view : views) {
            player.sendMap(view);
        }
    }

    private void tick() {
        if (!open || !player.isOnline()) {
            close();
            return;
        }
        // Kursor z obrotu głowy.
        Location location = player.getLocation();
        double[] cursor = CursorMath.cursor(location.getYaw(), location.getPitch());
        int cx = (int) Math.round(cursor[0]);
        int cy = (int) Math.round(cursor[1]);
        if (cx != lastCursorX || cy != lastCursorY) {
            lastCursorX = cx;
            lastCursorY = cy;
            listener.onCursor(cx, cy);
        }
        // Wejście ruchu.
        var input = player.getCurrentInput();
        boolean forward = input.isForward();
        boolean back = input.isBackward();
        boolean left = input.isLeft();
        boolean right = input.isRight();
        if (forward != lastForward || back != lastBack || left != lastLeft || right != lastRight) {
            lastForward = forward;
            lastBack = back;
            lastLeft = left;
            lastRight = right;
            listener.onMove(forward, back, left, right);
        }
        boolean jump = input.isJump();
        if (jump != lastJump) {
            lastJump = jump;
            listener.onJump(jump);
        }
        boolean sneak = input.isSneak();
        if (sneak != lastSneak) {
            lastSneak = sneak;
            listener.onSneak(sneak);
        }
        // Wysyłka brudnych kafli w limicie.
        int[] tiles = dirty.drain(System.nanoTime());
        if (tiles.length > 0) {
            sendTiles(tiles);
        }
    }

    private void sendTiles(int[] tiles) {
        for (int tile : tiles) {
            if (tile < 0 || tile >= views.size()) {
                continue;
            }
            player.sendMap(views.get(tile));
            dirty.addBytes(OverlayLayout.TILE * OverlayLayout.TILE);
        }
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public String id() {
        return spec.id();
    }

    @Override
    public OverlayCanvas canvas() {
        return canvas;
    }

    @Override
    public void onInput(InputListener listener) {
        this.listener = listener != null ? listener : new InputListener() {
        };
    }

    @Override
    public int scale() {
        return scale;
    }

    @Override
    public void scale(int value) {
        this.scale = Math.max(1, Math.min(16, value));
    }

    @Override
    public double centerX() {
        return centerX;
    }

    @Override
    public double centerZ() {
        return centerZ;
    }

    @Override
    public void center(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public long bytesSent() {
        return dirty.bytesSent();
    }

    @Override
    public double kilobytesPerSecond() {
        return dirty.kilobytesPerSecond(System.nanoTime());
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        onClosed.run();
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        try {
            listener.onClose();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Błąd w onClose nakładki " + spec.id() + ": " + exception.getMessage());
        }
        if (framesSpawned && player.isOnline()) {
            FakeFrames.destroy(player, entityIds);
            if (spec.hideHud()) {
                ClientView.show(player);
                ClientView.sendTime(player);
            }
        }
        if (seat != null && seat.isValid()) {
            seat.eject();
            seat.remove();
        }
        if (player.isOnline() && spec.restorePosition()) {
            Location current = player.getLocation();
            if (current.getWorld() != null && current.getWorld().getName().equals(worldName)
                    && current.distanceSquared(origin) <= RESTORE_DISTANCE * RESTORE_DISTANCE) {
                Runnable restore = () -> {
                    if (!player.isOnline() || player.isDead() || player.isInsideVehicle()) {
                        return;
                    }
                    Location now = player.getLocation();
                    if (now.getWorld() == null || !now.getWorld().getName().equals(worldName)
                            || now.distanceSquared(origin) > RESTORE_DISTANCE * RESTORE_DISTANCE) {
                        return;
                    }
                    player.teleport(origin);
                };
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, restore);
                } else {
                    restore.run();
                }
            }
        }
    }
}

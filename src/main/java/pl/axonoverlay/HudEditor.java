package pl.axonoverlay;

import java.util.function.Consumer;
import org.bukkit.map.MinecraftFont;

/** Coordinates shared with the HUD shaders: a reference viewport of 640 by 384. */
final class HudEditor implements InputListener {
    private static final int W = 640, H = 384;
    private static final byte FRAME = 87, ACTIVE = 126, WHITE = 34, INK = 119;
    private final OverlaySession session;
    private final int[][] boxes;
    private final int selected;
    private final Consumer<int[]> save;
    private int x = 320, y = 192, grabX, grabY, handle;
    private int[] original;
    private long lastClick;
    private byte[] background;
    private static final String[] LABELS = {"Tablica", "Minimapa", "Kompas"};

    HudEditor(OverlaySession session, int selected, int[][] boxes, Consumer<int[]> save) {
        this.session = session;
        this.selected = selected;
        this.boxes = java.util.Arrays.stream(boxes).map(int[]::clone).toArray(int[][]::new);
        this.save = save;
        session.onInput(this);
        render();
    }

    @Override public void onCursor(int px, int py) {
        if (x == px && y == py) return;
        int previousX = x, previousY = y;
        x = px; y = py;
        if (handle != 0) { move(); render(); return; }
        var canvas = session.canvas();
        for (int row=Math.max(0,previousY-4);row<=Math.min(H-1,previousY+4);row++) {
            int left=Math.max(0,previousX-4), right=Math.min(W,previousX+5);
            System.arraycopy(background,row*W+left,canvas.pixels(),row*W+left,right-left);
        }
        canvas.markDirty(previousX-4,previousY-4,9,9);
        cursor();
    }

    private void move() {
        int[] b = boxes[selected], a = original;
        int dx = x - grabX, dy = y - grabY;
        if (handle == 1) {
            b[0] = Math.clamp(a[0] + dx, 0, W - b[2]);
            b[1] = Math.clamp(a[1] + dy, 0, H - b[3]);
        } else if (handle == 2) {
            b[0] = Math.clamp(a[0] + dx, 0, a[0] + a[2] - 42);
            b[2] = a[2] + a[0] - b[0];
        } else if (handle == 3) b[2] = Math.clamp(a[2] + dx, 42, W - a[0]);
        else if (handle == 4) {
            b[1] = Math.clamp(a[1] + dy, 0, a[1] + a[3] - 30);
            b[3] = a[3] + a[1] - b[1];
        } else b[3] = Math.clamp(a[3] + dy, 30, H - a[1]);
    }

    @Override public void onScroll(int direction) {
        if (handle != 0 || direction == 0) return;
        int[] b = boxes[selected];
        double f = direction > 0 ? 1.08 : 1 / 1.08;
        double max = (double)W / b[2], min = 42.0 / b[2];
        if (selected != 2) { max = Math.min(max, (double)H / b[3]); min = Math.max(min, 30.0 / b[3]); }
        f = Math.clamp(f, min, max);
        int w = (int)Math.round(b[2]*f), h = selected == 2 ? b[3] : (int)Math.round(b[3]*f);
        b[0] = Math.clamp(b[0] + (b[2]-w)/2, 0, W-w);
        b[1] = Math.clamp(b[1] + (b[3]-h)/2, 0, H-h);
        b[2] = w; b[3] = h;
        render();
    }

    @Override public void onLeftClick(boolean down, int px, int py) {
        if (!down || System.nanoTime()-lastClick < 160_000_000L) return;
        lastClick = System.nanoTime();
        x = px; y = py;
        if (handle != 0) { move(); handle = 0; render(); return; }
        int[] b = boxes[selected];
        if (x >= b[0]-4 && x <= b[0]+b[2]+4 && y >= b[1]-4 && y <= b[1]+b[3]+4) {
            grabX = x; grabY = y; original = b.clone();
            if (Math.abs(x-b[0]) <= 6) handle = 2;
            else if (Math.abs(x-b[0]-b[2]) <= 6) handle = 3;
            else if (selected != 2 && Math.abs(y-b[1]) <= 6) handle = 4;
            else if (selected != 2 && Math.abs(y-b[1]-b[3]) <= 6) handle = 5;
            else handle = 1;
        }
        render();
    }

    @Override public void onSneak(boolean down) { if (down) session.close(); }
    @Override public void onJump(boolean down) {
        if (down) { var value = boxes[selected].clone(); session.close(); save.accept(value); }
    }

    private void label(int tx, int ty, String text) {
        var c = session.canvas();
        for (char ch : text.toCharArray()) {
            var glyph = MinecraftFont.Font.getChar(ch);
            if (glyph == null) continue;
            for (int j = 0; j < glyph.getHeight(); j++) for (int i = 0; i < glyph.getWidth(); i++)
                if (glyph.get(j,i)) c.set(tx+i,ty+j,WHITE);
            tx += glyph.getWidth()+1;
        }
    }

    private void render() {
        var c = session.canvas();
        c.clear((byte)0);
        for (int i = 0; i < boxes.length; i++) if (i != selected) frame(i);
        frame(selected);
        c.fillRect(90, H-17, 460, 15, INK);
        label(96,H-13,"LPM: chwyc / odloz   Rolka: rozmiar   Spacja: zapisz   Shift: anuluj");
        if (background == null) background = new byte[W*H];
        System.arraycopy(c.pixels(),0,background,0,W*H);
        cursor();
    }

    private void cursor() {
        var c = session.canvas();
        c.fillRect(x-4,y,9,1,WHITE); c.fillRect(x,y-4,1,9,WHITE);
    }

    private void frame(int i) {
        int[] b = boxes[i]; int l=b[0], t=b[1], w=b[2], h=b[3];
        byte color = i == selected ? ACTIVE : FRAME;
        var c = session.canvas();
        c.fillRect(l,t,w,2,color); c.fillRect(l,t+h-2,w,2,color);
        c.fillRect(l,t,2,h,color); c.fillRect(l+w-2,t,2,h,color);
        c.fillRect(l+2,t+2,Math.min(w-4,52),11,INK); label(l+4,t+3,LABELS[i]);
        if (i == selected) {
            c.fillRect(l,t+h/2-3,5,7,WHITE); c.fillRect(l+w-5,t+h/2-3,5,7,WHITE);
            if (i != 2) { c.fillRect(l+w/2-3,t,7,5,WHITE); c.fillRect(l+w/2-3,t+h-5,7,5,WHITE); }
        }
    }
}

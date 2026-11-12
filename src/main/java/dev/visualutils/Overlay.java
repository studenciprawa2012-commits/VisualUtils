package dev.visualutils;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Rysuje tracery, ramki ESP, nametagi i info na ekranie (HUD).
 * Pozycje w swiecie sa przeliczane na ekran, wiec nie potrzeba dodatkowych mixinow.
 */
public final class Overlay {

    private static final int COLOR_PLAYER = 0xFFFF5555;
    private static final int COLOR_MONSTER = 0xFFFFAA00;
    private static final int COLOR_MOB = 0xFF55FF55;
    private static final double MAX_DISTANCE = 128.0;
    private static final int MAX_TARGETS = 40;

    private Overlay() {}

    /** Dane kamery gracza potrzebne do rzutowania punktow 3D na ekran. */
    private record Cam(Vec3 eye, Vec3 fwd, Vec3 right, Vec3 up, double scale, double cx, double cy) {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) return;

        if (Config.isOn(Module.INFO)) {
            drawInfo(g, mc, player);
        }

        boolean worldModules = Config.isOn(Module.TRACERS) || Config.isOn(Module.ESP)
                || Config.isOn(Module.NAMETAGS);
        // rzutowanie dziala poprawnie tylko w widoku z pierwszej osoby
        if (!worldModules || !mc.options.getCameraType().isFirstPerson()) return;

        float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        double yaw = Math.toRadians(player.getViewYRot(pt));
        double pitch = Math.toRadians(player.getViewXRot(pt));
        Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        Vec3 up = right.cross(fwd);

        double fov = mc.options.fov().get();
        double scale = (height / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        Cam cam = new Cam(player.getEyePosition(pt), fwd, right, up, scale, width / 2.0, height / 2.0);

        boolean mobs = Config.isOn(Module.MOBS);
        int count = 0;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == player || !e.isAlive()) continue;
            boolean isPlayer = e instanceof Player;
            if (!isPlayer && !(mobs && e instanceof Mob)) continue;

            double dist = e.distanceTo(player);
            if (dist > MAX_DISTANCE) continue;
            if (++count > MAX_TARGETS) break;

            int color = isPlayer ? COLOR_PLAYER : (e instanceof Monster ? COLOR_MONSTER : COLOR_MOB);
            drawTarget(g, mc, cam, e, pt, dist, color, width, height);
        }
    }

    private static void drawTarget(GuiGraphics g, Minecraft mc, Cam cam, Entity e, float pt,
                                   double dist, int color, int width, int height) {
        Vec3 pos = e.getPosition(pt);
        double halfW = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();

        // ramka: rzutujemy 8 rogow bryly kolizji
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean allFront = true;
        for (int i = 0; i < 8; i++) {
            double px = pos.x + ((i & 1) == 0 ? -halfW : halfW);
            double py = pos.y + ((i & 2) == 0 ? 0.0 : h);
            double pz = pos.z + ((i & 4) == 0 ? -halfW : halfW);
            double[] s = project(cam, new Vec3(px, py, pz));
            if (s[2] <= 0.05) {
                allFront = false;
                break;
            }
            minX = Math.min(minX, s[0]);
            maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]);
            maxY = Math.max(maxY, s[1]);
        }
        if (allFront && (maxX - minX > width * 4 || maxY - minY > height * 4)) {
            allFront = false;
        }

        double[] center = project(cam, pos.add(0.0, h / 2.0, 0.0));
        boolean centerFront = center[2] > 0.05;

        if (Config.isOn(Module.ESP) && allFront) {
            drawRect(g, (int) minX, (int) minY, (int) maxX, (int) maxY, color);
        }

        if (Config.isOn(Module.TRACERS)) {
            double tx;
            double ty;
            if (centerFront) {
                tx = clamp(center[0], 0, width);
                ty = clamp(center[1], 0, height);
            } else {
                // cel jest za plecami - linia wskazuje kierunek do krawedzi ekranu
                double dx = center[3];
                double dy = -center[4];
                double len = Math.hypot(dx, dy);
                if (len < 1e-4) {
                    dx = 0.0;
                    dy = 1.0;
                    len = 1.0;
                }
                dx /= len;
                dy /= len;
                double kx = dx != 0.0 ? (width / 2.0) / Math.abs(dx) : Double.MAX_VALUE;
                double ky = dy != 0.0 ? (height / 2.0) / Math.abs(dy) : Double.MAX_VALUE;
                double k = Math.min(kx, ky);
                tx = width / 2.0 + dx * k;
                ty = height / 2.0 + dy * k;
            }
            drawLine(g, width / 2.0, height, tx, ty, color);
        }

        if (Config.isOn(Module.NAMETAGS)) {
            double tagX;
            double tagY;
            if (allFront) {
                tagX = (minX + maxX) / 2.0;
                tagY = minY - 12;
            } else if (centerFront) {
                tagX = center[0];
                tagY = center[1] - 12;
            } else {
                return;
            }

            StringBuilder text = new StringBuilder(e.getName().getString());
            if (e instanceof LivingEntity living) {
                text.append("  ").append((int) Math.ceil(living.getHealth()))
                        .append("/").append((int) Math.ceil(living.getMaxHealth())).append(" HP");
            }
            text.append("  ").append((int) dist).append("m");

            String s = text.toString();
            int tw = mc.font.width(s);
            int x = (int) tagX - tw / 2;
            int y = (int) tagY;
            g.fill(x - 2, y - 1, x + tw + 2, y + 9, 0x90000000);
            g.drawString(mc.font, s, x, y, 0xFFFFFFFF, false);
        }
    }

    private static void drawInfo(GuiGraphics g, Minecraft mc, LocalPlayer player) {
        String text = String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f   %s",
                player.getX(), player.getY(), player.getZ(), player.getDirection().getSerializedName());
        g.drawString(mc.font, text, 4, 4, 0xFFFFFFFF, true);
    }

    // ---------- pomocnicze ----------

    /** Zwraca {ekranX, ekranY, glebokosc, prawo, gora}. Glebokosc <= 0 oznacza punkt za plecami. */
    private static double[] project(Cam c, Vec3 p) {
        Vec3 d = p.subtract(c.eye());
        double depth = d.dot(c.fwd());
        double x = d.dot(c.right());
        double y = d.dot(c.up());
        double sx = c.cx();
        double sy = c.cy();
        if (depth > 0.05) {
            sx = c.cx() + x / depth * c.scale();
            sy = c.cy() - y / depth * c.scale();
        }
        return new double[]{sx, sy, depth, x, y};
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void drawRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Linia z malych kwadratow 2x2 (GuiGraphics nie ma wbudowanego rysowania linii). */
    private static void drawLine(GuiGraphics g, double x0, double y0, double x1, double y1, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        int steps = (int) Math.min(2000.0, Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 2.0));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (x0 + dx * t);
            int y = (int) (y0 + dy * t);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }
}

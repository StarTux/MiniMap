package com.cavetale.magicmap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

final class MagicMapRenderer extends MapRenderer {
    private final MagicMapPlugin plugin;

    MagicMapRenderer(MagicMapPlugin plugin) {
        super(true);
        this.plugin = plugin;
    }

    @Override
    public void initialize(MapView mapView) {
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        Session session = plugin.getSession(player);
        if (session.pasteMap != null) {
            session.pasteMap.paste(canvas);
            session.pasteMap = null;
        }
        if (session.pasteCursors != null) {
            canvas.setCursors(session.pasteCursors);
            session.pasteCursors = null;
        }
        // Schedule new
        if (!session.rendering) {
            long now = System.currentTimeMillis();
            if (now - session.lastRender > 1000L) {
                session.rendering = true;
                session.lastRender = now;
                Bukkit.getScheduler().runTask(this.plugin, () -> newRender(player, session));
            }
        }
        if (!session.cursoring) {
            session.cursoring = true;
            Bukkit.getScheduler().runTask(this.plugin, () -> newCursor(player, session));
        }
    }



    /**
     * {@link render(MapView, MapCanvas, Player)} will Schedule this
     * for exec in the main thread.
     */
    void newRender(Player player, Session session) {
        Location loc = player.getLocation();
        int centerX = loc.getBlockX();
        int centerZ = loc.getBlockZ();
        AsyncMapRenderer.Type type;
        String worldName = loc.getWorld().getName();
        if (loc.getWorld().getEnvironment() == World.Environment.NETHER) {
            type = AsyncMapRenderer.Type.NETHER;
        } else if (loc.getBlock().getLightFromSky() == 0) {
            Boolean enableCaveView = this.plugin.getEnableCaveView().get(worldName);
            if (enableCaveView == null) enableCaveView = this.plugin.getEnableCaveView().get("default");
            if (enableCaveView == null || enableCaveView == Boolean.TRUE) {
                type = AsyncMapRenderer.Type.CAVE;
            } else {
                type = AsyncMapRenderer.Type.SURFACE;
            }
        } else {
            type = AsyncMapRenderer.Type.SURFACE;
        }
        String worldDisplayName = this.plugin.getWorldNames().get(worldName);
        if (worldDisplayName == null) worldDisplayName = this.plugin.getWorldNames().get("default");
        AsyncMapRenderer renderer = new AsyncMapRenderer(this.plugin, session, type, worldDisplayName, centerX, centerZ, loc.getWorld().getTime());
        int ax = (centerX - 63) >> 4;
        int az = (centerZ - 63) >> 4;
        int bx = (centerX + 64) >> 4;
        int bz = (centerZ + 64) >> 4;
        for (int z = az; z <= bz; z += 1) {
            for (int x = ax; x <= bx; x += 1) {
                if (loc.getWorld().isChunkLoaded(x, z)) {
                    long chunkIndex = ((long)z << 32) + (long)x;
                    renderer.chunks.put(chunkIndex, loc.getWorld().getChunkAt(x, z).getChunkSnapshot());
                }
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, renderer);
    }

    /**
     * {@link render(MapView, MapCanvas, Player)} will Schedule this
     * for exec in the main thread.
     */
    void newCursor(Player player, Session session) {
        final Location loc = player.getLocation();
        final int px = loc.getBlockX();
        final int pz = loc.getBlockZ();
        MapCursorCollection cursors = new MapCursorCollection();
        cursors.addCursor(makeCursor(MapCursor.Type.WHITE_POINTER, loc, session.centerX, session.centerZ));
        for (Player o: player.getWorld().getPlayers()) {
            if (player.equals(o)) continue;
            if (!player.canSee(o)) continue;
            Location ol = o.getLocation();
            if (Math.abs(ol.getBlockX() - px) > 80) continue;
            if (Math.abs(ol.getBlockZ() - pz) > 80) continue;
            cursors.addCursor(makeCursor(MapCursor.Type.BLUE_POINTER, ol, session.centerX, session.centerZ));
        }
        for (Entity e: player.getNearbyEntities(32, 16, 32)) {
            if (e instanceof Player) continue;
            if (e instanceof org.bukkit.entity.Monster) {
                cursors.addCursor(makeCursor(MapCursor.Type.RED_POINTER, e.getLocation(), session.centerX, session.centerZ));
            } else if (e instanceof org.bukkit.entity.Creature) {
                cursors.addCursor(makeCursor(MapCursor.Type.SMALL_WHITE_CIRCLE, e.getLocation(), session.centerX, session.centerZ));
            }
        }
        session.pasteCursors = cursors;
        session.cursoring = false;
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, Location location, int centerX, int centerZ) {
        int dir = (int)(location.getYaw() + 11.25f);
        while (dir < 0) dir += 360;
        while (dir > 360) dir -= 360;
        dir = dir * 2 / 45;
        if (dir < 0) dir = 0;
        if (dir > 15) dir = 15;
        int x = (location.getBlockX() - centerX) * 2;
        int y = (location.getBlockZ() - centerZ) * 2;
        if (x < -127) x = -127;
        if (y < -119) y = -119;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte)x, (byte)y, (byte)dir, cursorType.getValue(), true);
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, Block block, int centerX, int centerZ) {
        int x = (block.getX() - centerX) * 2;
        int y = (block.getZ() - centerZ) * 2;
        if (x < -127) x = -127;
        if (y < -119) y = -119;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte)x, (byte)y, (byte)8, cursorType.getValue(), true);
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, int x, int y, int rot) {
        return new MapCursor((byte)((x - 64) * 2), (byte)((y - 64) * 2), (byte)rot, cursorType.getValue(), true);
    }
}
package net.gouge.paper;

import java.util.ArrayDeque;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

final class Session {
    int lockRefresh, lastUse, hangEnd = -1, grabTick = -1, baseHang, sneakOff = -1, softAt = -100, launchCharge;
    boolean dual, wasSneaking, noGrav;
    Material toolType, lockedType;
    Block pin;
    BlockFace pinFace;
    Vector anchor;
    Double slideVy, softVy;
    final ArrayDeque<Block> trail = new ArrayDeque<>();
}

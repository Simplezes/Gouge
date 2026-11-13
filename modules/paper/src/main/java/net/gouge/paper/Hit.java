package net.gouge.paper;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

record Hit(Block block, BlockFace face, Vector at) {
}

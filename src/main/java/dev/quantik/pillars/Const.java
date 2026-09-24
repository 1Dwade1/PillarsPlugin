package dev.quantik.pillars;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public class Const {
    public static final List<Material> BLOCK_BAN = Arrays.stream(new Material[]{
            Material.COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.ENDER_CHEST,
            Material.COMMAND_BLOCK_MINECART
    }).toList();
}

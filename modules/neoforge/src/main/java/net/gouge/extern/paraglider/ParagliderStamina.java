package net.gouge.extern.paraglider;

import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.stamina.Stamina;

public final class ParagliderStamina {
    private ParagliderStamina() {}

    public static boolean exhausted(Player player) {
        Stamina s = Stamina.get(player);
        return s.isDepleted() || s.stamina() <= 0;
    }
}

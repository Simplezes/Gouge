package net.gouge.extern.parcool;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

public final class ParCoolStamina {
    private static Method readableGet;
    private static Method isExhausted;
    private static Method value;
    private static Method localGet;
    private static Method consume;
    private static Method max;
    private static Method isInfinite;
    private static final boolean MODERN;
    private static final boolean LEGACY;
    private static double carry;

    static {
        boolean modern = false;
        boolean legacy = false;
        try {
            Class<?> readable = Class.forName("com.alrex.parcool.api.stamina.IReadableStamina");
            Class<?> local = Class.forName("com.alrex.parcool.api.stamina.AbstractLocalStamina");
            readableGet = readable.getMethod("get", Player.class);
            isExhausted = readable.getMethod("isExhausted");
            value = readable.getMethod("value");
            max = readable.getMethod("max");
            localGet = findGet(local);
            consume = local.getMethod("consume", double.class);
            isInfinite = local.getMethod("isInfinite");
            modern = localGet != null;
        } catch (ReflectiveOperationException | LinkageError e) {
            try {
                Class<?> stamina = Class.forName("com.alrex.parcool.common.capability.IStamina");
                readableGet = stamina.getMethod("get", Player.class);
                isExhausted = stamina.getMethod("isExhausted");
                value = stamina.getMethod("getActualValue");
                max = stamina.getMethod("getMaxStamina");
                consume = stamina.getMethod("consume", int.class);
                legacy = true;
            } catch (ReflectiveOperationException | LinkageError e2) {
                LogUtils.getLogger().warn("[Gouge] ParCool stamina API not found, using hang_time: {}", e2.toString());
            }
        }
        MODERN = modern;
        LEGACY = legacy;
    }

    private ParCoolStamina() {}

    private static Method findGet(Class<?> type) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals("get") && m.getParameterCount() == 1 && java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && m.getParameterTypes()[0].getName().equals("net.minecraft.client.player.LocalPlayer")) {
                return m;
            }
        }
        return null;
    }

    public static boolean available() {
        return MODERN || LEGACY;
    }

    public static boolean exhausted(Player player) {
        try {
            Object s = readableGet.invoke(null, player);
            return (Boolean) isExhausted.invoke(s) || ((Number) value.invoke(s)).doubleValue() <= 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static void spend(Player player, double fractionOfMax) {
        try {
            if (MODERN) {
                if (!localGet.getParameterTypes()[0].isInstance(player)) {
                    return;
                }
                Object s = localGet.invoke(null, player);
                if (!(Boolean) isInfinite.invoke(s)) {
                    consume.invoke(s, ((Number) max.invoke(s)).doubleValue() * fractionOfMax);
                }
            } else if (LEGACY) {
                Object s = readableGet.invoke(null, player);
                carry += ((Number) max.invoke(s)).doubleValue() * fractionOfMax;
                int whole = (int) carry;
                if (whole > 0) {
                    carry -= whole;
                    consume.invoke(s, whole);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogUtils.getLogger().debug("[Gouge] ParCool stamina spend failed: {}", e.toString());
        }
    }
}

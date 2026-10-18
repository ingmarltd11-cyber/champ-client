package net.champ.client.module.impl.misc;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.util.Inv;
import net.champ.addon.util.Slots;
import net.champ.client.event.impl.PacketSendEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.module.setting.StringSetting;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Misc deel 2: refills, streamer-mode, name-hide, texture-pack block, fast place/break. */
public final class MiscPack2 {
    private MiscPack2() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new CartRefillModule());
        out.add(new PotRefillModule());
        out.add(new StreamerModeModule());
        out.add(new NameHideModule());
        out.add(new NoForceResourcePackModule());
        out.add(new FastPlaceBreakModule());
        return out;
    }

    /** Cart Refill (misc): vult gekozen hotbar-slots aan met TNT-minecarts. */
    static final class CartRefillModule extends ModuleBase {
        final NumberSetting slotA = addSetting(new NumberSetting("Slot A", 7, 0, 8));
        final NumberSetting slotB = addSetting(new NumberSetting("Slot B", 8, 0, 8));

        CartRefillModule() {
            super("cart-refill", "Cart Refill",
                    "Vult hotbar-slots aan met TNT-minecarts.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            refill((int) slotA.get());
            refill((int) slotB.get());
        }

        private void refill(int hotbar) {
            Player p = Slots.player();
            if (p == null) return;
            var stack = p.getInventory().getItem(hotbar);
            if (!stack.isEmpty() && stack.getCount() > 4) return;
            int found = Inv.find("tnt_minecart");
            if (found >= 0) Inv.moveToHotbar(found, hotbar);
        }
    }

    /** Pot Refill: vult hotbar aan met gekozen splash-pots in gekozen slots. */
    static final class PotRefillModule extends ModuleBase {
        final StringSetting potion = addSetting(new StringSetting("Potion match", "splash_potion"));
        final NumberSetting slotA = addSetting(new NumberSetting("Slot A", 3, 0, 8));
        final NumberSetting slotB = addSetting(new NumberSetting("Slot B", 4, 0, 8));

        PotRefillModule() {
            super("pot-refill", "Pot Refill",
                    "Vult hotbar aan met splash-pots.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            refill((int) slotA.get());
            refill((int) slotB.get());
        }

        private void refill(int hotbar) {
            Player p = Slots.player();
            if (p == null) return;
            var stack = p.getInventory().getItem(hotbar);
            if (!stack.isEmpty() && stack.getCount() > 2) return;
            int found = Inv.find(potion.get().toLowerCase(Locale.ROOT));
            if (found >= 0) Inv.moveToHotbar(found, hotbar);
        }
    }

    /** Streamer Mode: verbergt alle namen in chat (censureert uitgaande berichten). */
    static final class StreamerModeModule extends ModuleBase {
        final BooleanSetting hideOwnName = addSetting(new BooleanSetting("Hide own name", true));
        final StringSetting replacement = addSetting(new StringSetting("Replacement", "***"));

        StreamerModeModule() {
            super("streamer-mode", "Streamer Mode",
                    "Verbergt namen in chat.", ModuleCategory.MISC);
        }

        @Override protected void onEnableInternal() {
            on(PacketSendEvent.TYPE, ev -> {
                String cn = ev.packet.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                if (!cn.contains("chat")) return;
                Player p = Slots.player();
                if (p == null || !hideOwnName.get()) return;
                try {
                    var msgField = ev.packet.getClass().getDeclaredField("message");
                    msgField.setAccessible(true);
                    Object msg = msgField.get(ev.packet);
                    if (msg instanceof String text && text.contains(p.getGameProfile().name())) {
                        msgField.set(ev.packet, text.replace(
                                p.getGameProfile().name(), replacement.get()));
                    }
                } catch (ReflectiveOperationException ignored) {}
            });
        }

        @Override protected void tick() {}
    }

    /** Name Hide: randomiseert je naam in chat (F5-label volgt via renderer-mixin roadmap). */
    static final class NameHideModule extends ModuleBase {
        final StringSetting fakeName = addSetting(new StringSetting("Fake name", "ChampUser"));

        NameHideModule() {
            super("name-hide", "Name Hide",
                    "Randomiseert je naam in chat.", ModuleCategory.MISC);
        }

        @Override protected void onEnableInternal() {
            on(PacketSendEvent.TYPE, ev -> {
                String cn = ev.packet.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                if (!cn.contains("chat")) return;
                Player p = Slots.player();
                if (p == null) return;
                try {
                    var msgField = ev.packet.getClass().getDeclaredField("message");
                    msgField.setAccessible(true);
                    Object msg = msgField.get(ev.packet);
                    if (msg instanceof String text && text.contains(p.getGameProfile().name())) {
                        msgField.set(ev.packet, text.replace(
                                p.getGameProfile().name(), fakeName.get()));
                    }
                } catch (ReflectiveOperationException ignored) {}
            });
        }

        @Override protected void tick() {}
    }

    /** No Force Texture Pack: weigert server-resourcepacks (DECLINED-status). */
    static final class NoForceResourcePackModule extends ModuleBase {
        NoForceResourcePackModule() {
            super("no-force-pack", "No Force Texture Pack",
                    "Voorkomt dat de server een texture pack forceert.", ModuleCategory.VISUALS);
        }

        @Override protected void onEnableInternal() {
            on(PacketSendEvent.TYPE, ev -> {
                String cn = ev.packet.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                if (!cn.contains("resourcepack")) return;
                // Herschrijf ACCEPTED/SUCCESSFULLY_LOADED naar DECLINED.
                for (var field : ev.packet.getClass().getDeclaredFields()) {
                    if (!field.getType().isEnum()) continue;
                    try {
                        field.setAccessible(true);
                        Object val = field.get(ev.packet);
                        if (val != null && !val.toString().equalsIgnoreCase("DECLINED")) {
                            for (Object c : field.getType().getEnumConstants()) {
                                if (c.toString().equalsIgnoreCase("DECLINED")) field.set(ev.packet, c);
                            }
                        }
                    } catch (ReflectiveOperationException ignored) {}
                }
            });
        }

        @Override protected void tick() {}
    }

    /** Fast Place/Break: snellere plaats- en break-delay. */
    static final class FastPlaceBreakModule extends ModuleBase {
        final NumberSetting placeDelay = addSetting(new NumberSetting("Place delay", 1, 0, 4));
        final BooleanSetting fastBreak = addSetting(new BooleanSetting("Fast break", true));

        FastPlaceBreakModule() {
            super("fast-place-break", "Fast Place/Break",
                    "Sneller plaatsen en breken.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            var mc = Slots.mc();
            Player p = Slots.player();
            if (p == null) return;
            try {
                var f = mc.getClass().getDeclaredField("rightClickDelay");
                f.setAccessible(true);
                f.setInt(mc, (int) placeDelay.get());
            } catch (ReflectiveOperationException ignored) {}
            if (fastBreak.get() && mc.options.keyAttack.isDown()
                    && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                mc.gameMode.startDestroyBlock(bhr.getBlockPos(), bhr.getDirection());
            }
        }
    }
}

package mindurka;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Reflect;
import mindurka.rules.MRules;
import mindurka.util.Report;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mods;
import mindustry.ui.Styles;

import java.nio.ByteBuffer;

public class MindurkaCompat {
    private MindurkaCompat() {}

    private static boolean initialized = false;
    public static void init() {
        if (initialized) return;
        initialized = true;

        try { // It ain't a server mod. Hop on Mindurka now, it's free!
            Class.forName("mindustry.server.ServerControl");
            return;
        } catch (ClassNotFoundException ignore) {}

        Events.on(EventType.WorldLoadEndEvent.class, event -> {
            MVars.rules = new MRules(Vars.state.rules, Vars.world.width(), Vars.world.height());
            if (MVars.rules.gamemode() != null && !MVars.mapEditor.isLoading() && (!Vars.net.active() || Vars.net.server())) {
                Log.info("Running local bullshit NOW");
                MVars.rules.gamemode().dataFixer();
                MVars.rules.gamemode().onStart();
            }
        });

        Events.on(EventType.ClientLoadEvent.class, event -> {
            @Nullable Mods.LoadedMod patchEditor = Vars.mods.getMod("patch-editor");
            MVars.patchEditorLoaded = patchEditor != null && patchEditor.enabled();

            MIcons.load();
            if (MVars.patchEditorLoaded) {
                // MindurkaCompat MUST apply patches last for consistent results.
                // Or does that mean patch editor goes last? Who knows!
                Core.app.post(() -> Core.app.post(() -> {
                    Injects.load();
                    try {
                        Class<?> eui = Class.forName("MinRi2.PatchEditor.ui.EUI");
                        try {
                            Reflect.invoke(eui, null, "addUI", Util.noargs);
                        } catch (Exception ignored) {
                            Reflect.invoke(eui, null, "mountEditor", Util.noargs);
                        }
                    } catch (Throwable e) {
                        Vars.ui.showException("Failed to initialize PatchEditor", e);
                        Log.err(e);
                        // Report.withException(e);
                    }
                }));
            } else Injects.load();


            ByteBuffer buffer = ByteBuffer.wrap(new byte[12]);
            Vars.netClient.addBinaryPacketHandler("mindurka.setData", packet -> {
                if (packet.length != 12) {
                    Log.warn("[mindurka.setData]: Invalid packet length!");
                    return;
                }

                buffer.clear();
                buffer.put(packet);
                short x = buffer.getShort(0);
                short y = buffer.getShort(2);
                long data = buffer.getLong(4);

                if (x < 0 || x >= Vars.world.width()) {
                    Log.warn("[mindurka.setData]: X ("+x+") is not within 0.."+Vars.world.width());
                    return;
                }
                if (y < 0 || y >= Vars.world.height()) {
                    Log.warn("[mindurka.setData]: Y ("+y+") is not within 0.."+Vars.world.height());
                    return;
                }
                Vars.world.tile(x, y).setPackedData(data);
            });

            Vars.ui.settings.shown(() -> {
                Table menu = Reflect.get(Vars.ui.settings, "menu");

                menu.button("@settings.mindurka", Icon.editor, Styles.flatt, Vars.iconMed, MVars.ui.mdSettings::show).marginLeft(8f).row();
            });
        });
    }
}

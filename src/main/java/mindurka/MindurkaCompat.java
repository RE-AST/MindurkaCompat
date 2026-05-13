package mindurka;

import arc.Core;
import arc.Events;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Reflect;
import mindurka.rules.MRules;
import mindurka.util.Report;
import mindustry.Vars;
import mindustry.editor.MapInfoDialog;
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
            if (MVars.rules.gamemode() != null && (!Core.settings.getBool("mindurka.enableeditor", true) ||
                    MVars.mapEditor != null || !MVars.mapEditor.isLoading()) && (!Vars.net.active() || Vars.net.server())) {
                MVars.rules.gamemode().dataFixer();
                MVars.rules.gamemode().onStart();
            }
        });

        Events.on(EventType.ClientLoadEvent.class, event -> {
            @Nullable Mods.LoadedMod patchEditor = Vars.mods.getMod("patch-editor");
            MVars.patchEditorLoaded = patchEditor != null && patchEditor.enabled();

            MIcons.load();
            Injects.load();

            if (MVars.patchEditorLoaded && Core.settings.getBool("mindurka.integrations.patcheditor", true) &&
                    Core.settings.getBool("mindurka.enableeditor", true)) {
                // Reapply patch editor patch.
                MapInfoDialog infoDialog = Reflect.get(MVars.editorDialog, "infoDialog");
                infoDialog.shown(() -> {
                    Core.app.post(() -> Core.app.post(() -> {
                        try {
                            Class<?> eui = Class.forName("dustdustry.patcheditor.ui.EUI");
                            Object patchManager = Reflect.get(eui, "manager");

                            ScrollPane pane = (ScrollPane) infoDialog.cont.getChildren().get(0);
                            Table table = Reflect.get(pane, "widget");

                            Table buttonTable = (Table) table.getChildren().peek();
                            if (buttonTable.find("patch-editor") != null) return;

                            buttonTable.row();

                            buttonTable.button(b -> {
                                        b.add("[accent][PE]").pad(8f).left();
                                        b.add("@patch-manager").expandX();
                                    }, Styles.cleari, () -> Reflect.invoke(patchManager, "show")).name("patch-editor")
                                    .colspan(buttonTable.getColumns()).width(Float.NEGATIVE_INFINITY).growX();

                            buttonTable.row();
                        } catch (Throwable e) {
                            Vars.ui.showException("Failed to initialize PatchEditor", e);
                            Log.err(e);
                        }
                    }));
                });
            }

            ByteBuffer buffer = ByteBuffer.wrap(new byte[12]);
            Vars.netClient.addBinaryPacketHandler("mindurka.setData", packet -> {
                try {
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
                } catch (Exception e) {
                    Log.err("Failed to set tile data", e);
                }
            });

            Vars.ui.settings.shown(() -> {
                Table menu = Reflect.get(Vars.ui.settings, "menu");

                menu.button("@settings.mindurka", Icon.editor, Styles.flatt, Vars.iconMed, MVars.ui.mdSettings::show).marginLeft(8f).row();
            });
        });
    }
}

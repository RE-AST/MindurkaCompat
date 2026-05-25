package mindurka.ui;

import arc.Core;
import arc.func.Cons;
import arc.input.KeyCode;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.SettingsMenuDialog;

public class MindurkaSettingsDialog extends Dialog {
    private SettingsMenuDialog.SettingsTable settingsTable = new SettingsMenuDialog.SettingsTable();
    public static Seq<ColorSettings> colors = new Seq<>();
    public static ObjectMap<String, Cons<Table>> previewProviders = new ObjectMap<>();
    private int lastColorCount = -1;

    public MindurkaSettingsDialog() {
        super(Core.bundle.get("settings.mindurka"));
        addCloseButton();
        setFillParent(true);
        title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image()).growX().height(3f).pad(4f).get().setColor(Pal.accent);

        Table content = new Table();
        content.defaults().left();

        ScrollPane pane = new ScrollPane(content);
        pane.setFadeScrollBars(false);

        settingsTable.checkPref("mindurka.enableeditor", true);
        settingsTable.checkPref("mindurka.enablenet", true);
        settingsTable.checkPref("mindurka.enablechat", true);
        settingsTable.checkPref("mindurka.enableinput", true);

        settingsTable.checkPref("mindurka.integrations.patcheditor", true);

        settingsTable.sliderPref("mindurka.zoomsensitivity", 100, 1, 200, x -> x + "%");

        settingsTable.sliderPref("mindurka.guideslinewidth", 1, 1, 20, x -> x + "px");
        settingsTable.checkPref("mindurka.guidesoutline", false);

        content.add(settingsTable).growX();
        content.row();

        content.table(colorContainer -> {
            colorContainer.left().defaults().left();
            colorContainer.update(() -> {
                if (lastColorCount != colors.size) {
                    lastColorCount = colors.size;
                    rebuildColors(colorContainer);
                }
            });
        }).growX().left();
        content.row();

        content.row();
        content.table(t -> {
            t.button(Core.bundle.get("settings.reset", "Reset to Defaults"), () -> {
                for (ColorSettings cs : colors) {
                    Core.settings.remove(cs.settingsKey);
                }
            }).margin(14).width(240f).pad(6);
        }).center().growX();

        top();

        cont.row();
        cont.add(pane);
    }

    private void rebuildColors(arc.scene.ui.layout.Table container) {
        container.clearChildren();
        if (colors.isEmpty()) return;

        OrderedMap<String, Seq<ColorSettings>> byMode = new OrderedMap<>();
        Seq<ColorSettings> ungrouped = new Seq<>();

        for (ColorSettings cs : colors) {
            if (cs.mode != null) {
                byMode.get(cs.mode, Seq::new).add(cs);
            } else {
                ungrouped.add(cs);
            }
        }

        for (ObjectMap.Entry<String, Seq<ColorSettings>> modeEntry : byMode) {
            container.add(modeEntry.key).color(Pal.accent).left().padLeft(8f).padTop(6f);
            container.row();
            container.table(grid -> buildColorGrid(grid, modeEntry.value)).left().padLeft(8f);
            container.row();
            Cons<Table> provider = previewProviders.get(modeEntry.key);
            if (provider != null) {
                container.table(provider).left().padLeft(8f).padTop(4f);
                container.row();
            }
        }

        if (!ungrouped.isEmpty()) {
            container.add("@color").left().padLeft(8f).padTop(6f);
            container.row();
            container.table(grid -> buildColorGrid(grid, ungrouped)).left().padLeft(8f);
            container.row();
        }
    }

    private void buildColorGrid(arc.scene.ui.layout.Table grid, Seq<ColorSettings> items) {
        grid.defaults().pad(2);

        Seq<String> columns = new Seq<>();
        OrderedMap<String, OrderedMap<String, ColorSettings>> rows = new OrderedMap<>();
        Seq<ColorSettings> flat = new Seq<>();

        for (ColorSettings cs : items) {
            if (cs.category == null || cs.column == null) {
                flat.add(cs);
                continue;
            }
            if (!columns.contains(cs.column)) columns.add(cs.column);
            rows.get(cs.category, OrderedMap::new).put(cs.column, cs);
        }

        if (!rows.isEmpty()) {
            grid.add("").left();
            for (String col : columns) {
                grid.add(col).color(Pal.accent).center();
            }
            grid.row();

            for (ObjectMap.Entry<String, OrderedMap<String, ColorSettings>> entry : rows) {
                grid.add(entry.key).left().padLeft(4);
                for (String col : columns) {
                    ColorSettings cs = entry.value.get(col);
                    if (cs != null) {
                        addColorButton(grid, cs);
                    } else {
                        grid.add("--").color(Pal.lightishGray).center();
                    }
                }
                grid.row();
            }
        }

        for (ColorSettings cs : flat) {
            grid.add("@" + cs.tlKey).left().padLeft(4);
            addColorButton(grid, cs);
            grid.row();
        }
    }

    private void addColorButton(arc.scene.ui.layout.Table grid, ColorSettings cs) {
        grid.button(b -> {
            b.table(Tex.pane, in -> in.stack(new Image(Tex.alphaBg), new Image(Tex.whiteui) {{
                update(() -> setColor(cs.getColor()));
            }}).grow()).margin(2).size(24);
        }, () -> Vars.ui.picker.show(cs.getColor(), c -> {
            Core.settings.put(cs.settingsKey, c.toString());
        })).size(32).center();
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back) hide();
        });
    }
}

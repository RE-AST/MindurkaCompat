package mindurka.ui;

import arc.Core;
import arc.input.KeyCode;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;

public class MindurkaSettingsDialog extends Dialog {
    private Table settingsTable = new Table();

    public MindurkaSettingsDialog() {
        super(Core.bundle.get("settings.mindurka"));
        addCloseButton();
        setFillParent(true);
        title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image()).growX().height(3f).pad(4f).get().setColor(Pal.accent);

        ScrollPane pane = new ScrollPane(settingsTable);
        pane.setFadeScrollBars(false);

        top();

        cont.row();
        cont.add(pane);
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back) hide();
        });
    }
}

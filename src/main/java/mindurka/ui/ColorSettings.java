package mindurka.ui;

import arc.Core;
import arc.graphics.Color;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(of = "settingsKey")
public class ColorSettings {
    public final String tlKey;
    public final String settingsKey;
    public final Color def;
    public final String category;
    public final String column;
    public final String mode;

    public ColorSettings(String tlKey, String settingsKey, Color def) {
        this(tlKey, settingsKey, def, null, null, null);
    }

    public ColorSettings(String tlKey, String settingsKey, Color def, String category, String column) {
        this(tlKey, settingsKey, def, category, column, null);
    }

    public ColorSettings(String tlKey, String settingsKey, Color def, String category, String column, String mode) {
        this.tlKey = tlKey;
        this.settingsKey = settingsKey;
        this.def = def;
        this.category = category;
        this.column = column;
        this.mode = mode;
    }

    public Color getColor(){
        return Color.valueOf(Core.settings.getString(settingsKey, def.toString()));
    }

    public Color add(){
        Core.settings.defaults(settingsKey, def.toString());
        MindurkaSettingsDialog.colors.addUnique(this);
        return getColor();
    }
}

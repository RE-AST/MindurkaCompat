package mindurka.rules;

import arc.graphics.Color;
import mindustry.game.Team;
import mindurka.ui.ColorSettings;
import mindurka.ui.FortsPlotPreview;
import mindurka.ui.MindurkaSettingsDialog;

// I did have fun with those yes
public enum FortsPlotState {
    /** Plot is disabled and will be removed from the map as the game starts.
     *  <p>
     *  This is the only plot type that does not respect the assigned team.
     */
    disabled,
    /** Plot can be placed. */
    enabled,
    /** Plot is placed. */
    placed,
    /**
     * Plot is placed and cannot be destroyed while team still exists. Otherwise,
     * acts as if plot is {@link #placed}.
     */
    locked,
    /** Plot cannot be destroyed. */
    static_,
    /** Game pretends there's a plot there. */
    ghost,

    ;

    public static final ColorSettings[] strokeSettings = new ColorSettings[] {
            new ColorSettings("forts.plot.disabled.stroke", "mindurka.forts.disabled.stroke", new Color(0x3D3846ff), "disabled", "Stroke", "Forts plots"),
            new ColorSettings("forts.plot.enabled.stroke", "mindurka.forts.enabled.stroke", new Color(0xE0B310ff), "enabled", "Stroke", "Forts plots"),
            new ColorSettings("forts.plot.placed.stroke", "mindurka.forts.placed.stroke", new Color(0xE0B310ff), "placed", "Stroke", "Forts plots"),
            new ColorSettings("forts.plot.locked.stroke", "mindurka.forts.locked.stroke", new Color(0x9BE4F8ff), "locked", "Stroke", "Forts plots"),
            new ColorSettings("forts.plot.static.stroke", "mindurka.forts.static.stroke", new Color(0xC25A00ff), "static", "Stroke", "Forts plots"),
            new ColorSettings("forts.plot.ghost.stroke", "mindurka.forts.ghost.stroke", new Color(0xCDCDCDff), "ghost", "Stroke", "Forts plots"),
    };
    public static final ColorSettings[] outlineSettings = new ColorSettings[] {
            new ColorSettings("forts.plot.disabled.outline", "mindurka.forts.disabled.outline", new Color(0xCDCDCDff), "disabled", "Outline", "Forts plots"),
            new ColorSettings("forts.plot.enabled.outline", "mindurka.forts.enabled.outline", new Color(0x5D2C72ff), "enabled", "Outline", "Forts plots"),
            new ColorSettings("forts.plot.placed.outline", "mindurka.forts.placed.outline", new Color(0x5D2C72ff), "placed", "Outline", "Forts plots"),
            new ColorSettings("forts.plot.locked.outline", "mindurka.forts.locked.outline", new Color(0x2A1E13ff), "locked", "Outline", "Forts plots"),
            new ColorSettings("forts.plot.static.outline", "mindurka.forts.static.outline", new Color(0x0C2D55ff), "static", "Outline", "Forts plots"),
            new ColorSettings("forts.plot.ghost.outline", "mindurka.forts.ghost.outline", Color.black, "ghost", "Outline", "Forts plots"),
    };
    public static final ColorSettings[] fillSettings = new ColorSettings[] {
            new ColorSettings("forts.plot.disabled.fill", "mindurka.forts.disabled.fill", Color.clear, "disabled", "Fill", "Forts plots"),
            new ColorSettings("forts.plot.enabled.fill", "mindurka.forts.enabled.fill", Color.clear, "enabled", "Fill", "Forts plots"),
            null,
            null,
            null,
            null,
    };

    @Override
    public String toString() {
        if (this == static_) return "static";
        return name();
    }

    public Color outline() { return outlineSettings[ordinal()].getColor(); }
    public Color stroke() { return strokeSettings[ordinal()].getColor(); }
    public Color fill(Color copyTo, Team team) {
        Color color = copyTo == null ? new Color() : copyTo;
        ColorSettings fs = fillSettings[ordinal()];
        Color target = fs != null ? fs.getColor() : team.color;
        color.set(target);
        if (color.a > 0.4f) color.a = 0.4f;
        return color;
    }

    public static void registerColors() {
        for (ColorSettings s : strokeSettings) s.add();
        for (ColorSettings s : outlineSettings) s.add();
        for (ColorSettings s : fillSettings) if (s != null) s.add();

        MindurkaSettingsDialog.previewProviders.put("Forts", preview -> {
            preview.add(new FortsPlotPreview()).size(480, 360).pad(4);
        });
    }

    /** Check if scheme needs to be placed. */
    public boolean placeTemplate() {
        switch (this) {
            case placed:
            case locked:
            case static_:
                return true;
            default:
                return false;
        }
    }

    /** Check if plot is considered "placed". */
    public boolean placed() {
        switch (this) {
            case placed:
            case locked:
            case static_:
            case ghost:
                return true;
            default:
                return false;
        }
    }
}

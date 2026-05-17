package mindurka;

import arc.util.Nullable;import mindurka.fs.FileSystem;import mindurka.rules.MRules;
import mindurka.ui.*;
import mindustry.Vars;
import mindustry.editor.MapView;

public class MVars {
    private MVars() {}

    public static MRules rules;

    public static final int version = 7;
    public static MapView oldMapView;
    public static OMapView mapView;
    public static OMapEditor mapEditor;
    public static OEditorDialog editorDialog;
    public static OCustomRulesDialog customRulesDialog;
    public static ToolOptions toolOptions = new ToolOptions();
    public static Protocol protocol = new Protocol();
    public static @Nullable FileSystem fileSystem = null;
    public static Ui ui = new Ui();
    public static OTextureAtlas atlas;

    public static boolean patchEditorLoaded = false;

    // FIXME: This is bullshit.

    private static BitMap mapbits = null;
    public static BitMap mapbits() {
        if (mapbits == null || mapbits.width != Vars.world.width() || mapbits.height != Vars.world.height()) {
            mapbits = BitMap.of(Vars.world.tiles);
        }
        return mapbits;
    }
    private static BitMap mapbits2 = null;
    public static BitMap mapbits2() {
        if (mapbits2 == null || mapbits2.width != Vars.world.width() || mapbits2.height != Vars.world.height()) {
            mapbits2 = BitMap.of(Vars.world.tiles);
        }
        return mapbits2;
    }
}

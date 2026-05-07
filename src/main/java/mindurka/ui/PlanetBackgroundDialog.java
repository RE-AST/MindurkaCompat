package mindurka.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.layout.Table;
import mindustry.graphics.Pal;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.gen.Icon;
import mindustry.type.Planet;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.*;

public class PlanetBackgroundDialog extends BaseDialog {
    private Table main;
    private PlanetParams params;

    private float rotX = 90f;
    private float rotY = 90f;
    private float zoom = 1f;

    private static final float DRAG_SPEED  = 0.3f;
    private static final float SCROLL_SPEED = 0.1f;

    public PlanetBackgroundDialog() {
        super("@rules.planetbackground", new DialogStyle() {{
            stageBackground = Styles.none;
            titleFont      = Fonts.def;
            titleFontColor = Pal.accent;
        }});

        addCloseButton();
        buttons.button("@rules.background.removebackground", Icon.trash, () -> {
            if (state.rules.planetBackground != null) ui.showConfirm("@rules.background.removalwarning", this::removeBackground);
        }).size(210f, 64f);
        buttons.button("@rules.background.selectplanet", Icon.planet, this::planetDialog).size(210f, 64f);

        titleTable.row();
        titleTable.add("@rules.background.description").color(Color.white).padBottom(20f);

        addListener(new InputListener() {
            private float lastX, lastY;
            private boolean dragging = false;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (state.rules.planetBackground == null || button != KeyCode.mouseRight) return false;
                lastX = x;
                lastY = y;
                dragging = true;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging = false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (!dragging || state.rules.planetBackground == null) return;
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;

                rotX = Mathf.mod(rotX + dx * DRAG_SPEED, 360f);
                rotY = Mathf.clamp(rotY + dy * DRAG_SPEED, 1f, 179f);
                updateRotation();
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY) {
                if (state.rules.planetBackground == null) return false;
                zoom = Mathf.clamp(zoom * (1f + amountY * SCROLL_SPEED), 0.1f, 10f);
                updateRotation();
                return true;
            }
        });

        shown(this::setup);
    }

    private void addBackground() {
        rotX = 90f;
        rotY = 90f;
        zoom = 1f;
        state.rules.planetBackground = new PlanetParams();
        params = state.rules.planetBackground;
        setup();
    }

    private void removeBackground() {
        state.rules.planetBackground = null;
        PlanetBackgroundDrawer.update();
        setup();
    }

    private void addPlanetButton(Planet planet, Table tb, BaseDialog dialog) {
        tb.button(planet.localizedName, Icon.planet, Styles.togglet, () -> {
            params.planet = planet;
            updateRotation();
            dialog.hide();
        }).marginLeft(14f).padBottom(5f).width(220f).height(55f)
          .checked(params.planet == planet)
          .update(b -> b.setChecked(params.planet == planet))
          .get().getChildren().get(1).setColor(planet.iconColor);
    }

    private void planetDialog() {
        if (params == null) return;

        BaseDialog dialog = new BaseDialog("@rules.background.selectplanet");
        dialog.cont.pane(p -> p.table(t -> {
            int i = 0;
            for (Planet planet : content.planets()) {
                addPlanetButton(planet, t, dialog);
                if (++i % 3 == 0) t.row();
            }
        }));
        dialog.addCloseButton();
        dialog.show();
    }

    private void setup() {
        params = state.rules.planetBackground;

        if (params != null) {
            zoom = params.zoom;
            if (params.camPos != null && !params.camPos.isZero()) {
                Vec3 dir = params.camPos.cpy().nor();
                rotY = (float)Math.acos(dir.y) * Mathf.radDeg;
                rotX = Mathf.mod(Mathf.atan2(dir.z, dir.x) * Mathf.radDeg, 360f);
            }
        }

        cont.clear();
        cont.table(t -> main = t);

        if (params == null) {
            main.add("@rules.background.nobackground").color(Color.white).padBottom(20f).row();
            main.button("@rules.background.addbackground", Styles.togglet, this::addBackground)
                .marginLeft(14f).width(220f).height(55f);
        }

        updateRotation();
    }

    private void updateRotation() {
        PlanetParams p = state.rules.planetBackground;
        if (p == null) return;
        p.camPos = new Vec3(
            Mathf.cosDeg(rotX) * Mathf.sinDeg(rotY),
            Mathf.cosDeg(rotY),
            Mathf.sinDeg(rotX) * Mathf.sinDeg(rotY)
        );
        p.zoom = zoom;
        PlanetBackgroundDrawer.update();
    }

    @Override
    public void draw() {
        Texture tex = params != null ? PlanetBackgroundDrawer.draw() : null;
        if (tex != null) {
            float drawSize = Math.max(Core.graphics.getWidth(), Core.graphics.getHeight());
            Draw.rect(
                Draw.wrap(tex),
                Core.graphics.getWidth()  / 2f,
                Core.graphics.getHeight() / 2f,
                drawSize, -drawSize
            );
            Draw.flush();
        } else {
            Draw.color(color.r, color.g, color.b, color.a * parentAlpha);
            Styles.black9.draw(x, y, width, height);
        }

        super.draw();
    }
}

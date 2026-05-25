package mindurka.ui;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindurka.rules.FortsPlotState;

public class FortsPlotPreview extends Element {
    private static final int COLS = 3;
    private static final int ROWS = 2;
    private static final float TILE_SIZE = 12f;
    private final Color tmpColor = new Color();

    @Override
    public void draw() {
        validate();

        TextureRegion waterRegion = Blocks.water.region;
        Draw.color();
        for (float tx = x; tx < x + width; tx += TILE_SIZE) {
            for (float ty = y; ty < y + height; ty += TILE_SIZE) {
                float tw = Math.min(TILE_SIZE, x + width - tx);
                float th = Math.min(TILE_SIZE, y + height - ty);
                Draw.rect(waterRegion, tx + tw / 2f, ty + th / 2f, tw, th);
            }
        }

        float pad = 8f, padY = 16f;
        FortsPlotState[] states = FortsPlotState.values();
        float cellW = (width - pad * 2f) / COLS;
        float cellH = (height - padY * 2f) / ROWS;
        float plotW = cellW - 10f;
        float plotH = cellH - 16f;

        Font font = Scl.scl(1f) > 1f ? Fonts.outline : Fonts.def;
        float fontSize = 0.45f;

        for (int i = 0; i < states.length; i++) {
            FortsPlotState state = states[i];
            int col = i % COLS;
            int row = i / COLS;

            float cx = x + pad + cellW * col + cellW / 2f;
            float cy = y + height - padY - cellH * row - cellH / 2f;

            state.fill(tmpColor, Team.sharded);
            if (tmpColor.a > 0f) {
                Draw.color(tmpColor);
                Fill.rect(cx, cy, plotW, plotH);
            }

            Draw.color(state.outline());
            Lines.stroke(Scl.scl(3f));
            Lines.rect(cx - plotW / 2f, cy - plotH / 2f, plotW, plotH);

            Draw.color(state.stroke());
            Lines.stroke(Scl.scl(1.5f));
            Lines.rect(cx - plotW / 2f, cy - plotH / 2f, plotW, plotH);

            Draw.color(Pal.lightishGray);
            font.getData().setScale(fontSize);
            font.draw(state.toString(), cx, cy - plotH / 2f - 3f, 0f, Align.center, false);
            font.getData().setScale(1f);
        }

        Draw.reset();
    }
}

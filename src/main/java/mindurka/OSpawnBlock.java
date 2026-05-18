package mindurka;

import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import mindurka.ui.EditorTile;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.SpawnBlock;

public class OSpawnBlock extends SpawnBlock {
    public OSpawnBlock(String name) {
        super(name);
    }

    @Override
    public void drawBase(Tile tile) {
        if (tile instanceof EditorTile) {
            if(tile.build != null){
                tile.build.draw();
            }else{
                Draw.rect(
                        variants == 0 ? region :
                                variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))],
                        tile.drawx(), tile.drawy());
            }
        }
    }
}

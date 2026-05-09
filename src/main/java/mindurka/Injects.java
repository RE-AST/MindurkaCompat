package mindurka;

import arc.Core;
import mindurka.rules.FortsPlotKind;
import mindurka.rules.Gamemode;
import mindurka.ui.OCustomRulesDialog;
import mindurka.ui.OEditorDialog;
import mindurka.ui.OMapEditor;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.payloads.*;
import mindustry.world.consumers.Consume;

public class Injects {
    public static void load() {
        Vars.content.blocks().each(x -> x instanceof PayloadDeconstructor, x -> {
            x.buildType = () -> ((PayloadDeconstructor) x).new PayloadDeconstructorBuild() {
                @Override
                public boolean acceptPayload(Building source, Payload payload) {
                    return super.acceptPayload(source, payload) &&
                            (!(payload instanceof BuildPayload) ||
                                    ((BuildPayload) payload).build.block != Blocks.carbideWallLarge ||
                                    !Vars.state.rules.tags.get("mindurkaGamemode", "").equals("forts"));
                }
            };
        });
        Vars.content.blocks().each(x -> x instanceof OverdriveProjector, x -> x.buildType = () -> ((OverdriveProjector) x).new OverdriveBuild() {
            @Override
            public void updateConsumption() {
                if (this.block.hasConsumers && (!this.cheating() || (MVars.rules.overdriveIgnoresCheat() && !MVars.rules.legacyServer()))) {
                    if (!this.enabled) {
                        this.potentialEfficiency = this.efficiency = this.optionalEfficiency = 0.0F;
                        this.shouldConsumePower = false;
                    } else {
                        boolean update = this.shouldConsume() && this.productionValid();
                        float minEfficiency = 1.0F;
                        this.efficiency = this.optionalEfficiency = 1.0F;
                        this.shouldConsumePower = true;

                        for (Consume cons : this.block.nonOptionalConsumers) {
                            float result = cons.efficiency(this);
                            if (cons != this.block.consPower && result <= 1.0E-7F) {
                                this.shouldConsumePower = false;
                            }

                            minEfficiency = Math.min(minEfficiency, result);
                        }

                        for(Consume cons : this.block.optionalConsumers) {
                            this.optionalEfficiency = Math.min(this.optionalEfficiency, cons.efficiency(this));
                        }

                        this.efficiency = minEfficiency;
                        this.optionalEfficiency = Math.min(this.optionalEfficiency, minEfficiency);
                        this.potentialEfficiency = this.efficiency;
                        if (!update) {
                            this.efficiency = this.optionalEfficiency = 0.0F;
                        }

                        this.updateEfficiencyMultiplier();
                        if (update && this.efficiency > 0.0F) {
                            for(Consume cons : this.block.updateConsumers) {
                                cons.update(this);
                            }
                        }

                    }
                } else {
                    this.potentialEfficiency = this.enabled && this.productionValid() ? 1.0F : 0.0F;
                    this.efficiency = this.optionalEfficiency = this.shouldConsume() ? this.potentialEfficiency : 0.0F;
                    this.shouldConsumePower = true;
                    this.updateEfficiencyMultiplier();
                }
            }
        });

        Gamemode.init();
        FortsPlotKind.init();

        if (Core.settings.getBool("mindurka.enableeditor", true)) {
            MVars.oldMapView = Vars.ui.editor.getView();

            OMapEditor.inject();
            MVars.mapEditor = (OMapEditor) Vars.editor;
            Vars.ui.editor = MVars.editorDialog = new OEditorDialog(MVars.mapEditor, Vars.ui.editor);
            OCustomRulesDialog.inject();
        }
        if (Core.settings.getBool("mindurka.enableinput", true))
            OMobileInput.inject();
    }
}

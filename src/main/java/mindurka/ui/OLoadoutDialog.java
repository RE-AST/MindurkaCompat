package mindurka.ui;

import arc.func.Boolf;
import arc.scene.ui.Dialog;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Structs;
import mindurka.Util;
import mindurka.util.Report;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.ItemSeq;
import mindustry.type.ItemStack;
import mindustry.ui.dialogs.LoadoutDialog;

public class OLoadoutDialog extends LoadoutDialog {
    private Runnable updater() {
        return Reflect.get(LoadoutDialog.class, this, "updater");
    }
    private Runnable resetter() {
        return Reflect.get(LoadoutDialog.class, this, "resetter");
    }

    public OLoadoutDialog() {
        super();

        getChildren().get(getChildren().size - 1).clicked(() -> {
            try {
                resetter().run();
                reseed();
                if (updater() != null) updater();
                Runnable setup = Reflect.get(LoadoutDialog.class, this, "setup");
                if (setup != null) setup.run();
            } catch (Throwable t) {
                Report.withException(t);
            }
        });
    }

    @Override
    public void show(int capacity, ItemSeq total, Seq<ItemStack> stacks, Boolf<Item> validator, Runnable reseter, Runnable updater, Runnable hider) {
        super.show(capacity, total, stacks, validator, reseter, updater, hider);
        reseed();
    }

    private void reseed() {
        Seq<ItemStack> originalStacks = Reflect.get(LoadoutDialog.class, this, "originalStacks");
        Boolf<Item> validator = Reflect.get(LoadoutDialog.class, this, "validator");

        Seq<ItemStack> stacks = originalStacks.map(ItemStack::copy);
        stacks.addAll(Vars.content.items().select(i -> validator.get(i) && !stacks.contains(stack -> stack.item == i)).map(i -> new ItemStack(i, 0)));
        stacks.sort(Structs.comparingInt(s -> s.item.id));
        Reflect.set(LoadoutDialog.class, this, "stacks", stacks);
    }
}

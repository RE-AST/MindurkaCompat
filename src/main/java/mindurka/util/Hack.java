package mindurka.util;

import arc.func.Func;
import arc.func.Prov;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.ctype.Content;
import mindustry.ctype.MappableContent;
import mindustry.mod.Mods;

public class Hack {
    private Hack() {}

    public static void replaceContent(MappableContent original, Func<String, MappableContent> newContent) {
        Mods.LoadedMod currentMod = Reflect.get(ContentLoader.class, Vars.content, "currentMod");
        Reflect.set(ContentLoader.class, Vars.content, "currentMod", null);
        ObjectMap<String, MappableContent>[] contentNameMap = Reflect.get(ContentLoader.class, Vars.content, "contentNameMap");
        Seq<Content>[] contentMap = Reflect.get(ContentLoader.class, Vars.content, "contentMap");
        ObjectMap<String, MappableContent> nameeMap = contentNameMap[original.getContentType().ordinal()];
        ObjectMap<String, MappableContent> nameMap = Reflect.get(ContentLoader.class, Vars.content, "nameMap");

        if (!nameeMap.containsKey(original.name)) throw new IllegalArgumentException("Content was not registered!");

        nameeMap.remove(original.name);
        nameMap.remove(original.name);
        contentMap[original.getContentType().ordinal()].remove(original);

        MappableContent content = newContent.get(original.name);
        content.loadIcon();
        content.load();

        Reflect.set(ContentLoader.class, Vars.content, "currentMod", currentMod);
    }
}

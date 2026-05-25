# MindurkaCompat

The official mod for improving compatibility with Mindurka gamemodes.

## Compatibility

This mod is incompatible with:
- Mapping utilities (both completely ~~break~~ change the editor, thus compatibility is impossible).

This mod is partially incompatible with:
- [Scheme Size](https://github.com/00SunRay00/scheme-size-port) (Both override input handler. This may be addressed in the future, but that's how it is right now).

Compatibility is provided for:
- [PatchEditor](https://github.com/Dustdustry/PatchEditor) (MindurkaCompat replaces editor dialog, making it impossible for PatchEditor to add its button).

In general, we may break compatibility with any mod if we find it necessary for whatever feature. If there's a way
to make them compatible again (assuming it's not some cheat mod we don't care about), we may do that. Not using a
library mod is always preferred to using one. Soft dependencies are better than hard dependencies.

## Thanks

- [Mapping Utilities](https://github.com/ApsZoldat/mindustry-mapping-utilities), where I stole editor code from.
package net.cycle_paintings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

final class PaintingCycle {
    private record Size(int width, int height) {}
    private final Map<Size, List<Holder<PaintingVariant>>> groups = new HashMap<>();

    PaintingCycle(Registry<PaintingVariant> registry, ModConfig config) {
        var exclusions = config.exclusions();
        // Tag order is shared with vanilla/modded placement; do not hard-code vanilla art.
        for (var holder : registry.getTagOrEmpty(PaintingVariantTags.PLACEABLE)) {
            var key = holder.unwrapKey();
            if (key.isEmpty()) continue;
            var id = key.get().identifier();
            boolean excluded = ModConfig.excludes(exclusions, id);
            if (config.showRegisteredPaintingsDebug()) {
                CyclePaintings.LOGGER.debug("[Cycle Paintings] Registered painting {} ({})", id,
                        excluded ? "ignored" : "allowed");
            }
            if (!excluded) {
                var art = holder.value();
                groups.computeIfAbsent(new Size(art.width(), art.height()), unused -> new ArrayList<>()).add(holder);
            }
        }
    }

    Holder<PaintingVariant> next(Holder<PaintingVariant> current, boolean backwards) {
        var art = current.value();
        var candidates = groups.get(new Size(art.width(), art.height()));
        if (candidates == null) return null;
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).equals(current)) {
                return candidates.get(Math.floorMod(index + (backwards ? -1 : 1), candidates.size()));
            }
        }
        // An excluded or non-placeable painting must not become a way into the cycle.
        return null;
    }
}

package net.cycle_paintings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;

record ModConfig(String ignorePaintingsInCycleIdentifier, boolean showRegisteredPaintingsDebug) {
    static final ModConfig DEFAULT = new ModConfig("infernalexp,", false);
    private static final String TEMPLATE = """
            {
              "ignorePaintingsInCycleIdentifier": "infernalexp,",
              "showRegisteredPaintingsDebug": false
            }
            """;

    static ModConfig load(Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, TEMPLATE, StandardOpenOption.CREATE_NEW);
                return DEFAULT;
            }
            try (var reader = Files.newBufferedReader(path)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                // Accept the older documented option names as well as the current name.
                String key = "ignorePaintingsInCycleIdentifier";
                if (!json.has(key)) key = "ignorePaintingsInCycleResourceLocation";
                if (!json.has(key)) key = "ignorePaintingsInCycleResourceLocationPrefix";
                String ignored = DEFAULT.ignorePaintingsInCycleIdentifier;
                if (json.has(key)) {
                    if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
                        throw new IllegalArgumentException("The painting exclusion list must be a string");
                    ignored = json.get(key).getAsString();
                }
                boolean debug = false;
                if (json.has("showRegisteredPaintingsDebug")) {
                    var value = json.get("showRegisteredPaintingsDebug");
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                        throw new IllegalArgumentException("showRegisteredPaintingsDebug must be a boolean");
                    debug = value.getAsBoolean();
                }
                return new ModConfig(ignored, debug);
            }
        } catch (IOException | RuntimeException exception) {
            CyclePaintings.LOGGER.warn("[Cycle Paintings] Could not load config {}; using defaults. Existing files are preserved.",
                    path, exception);
            return DEFAULT;
        }
    }

    Set<String> exclusions() {
        return Arrays.stream(ignorePaintingsInCycleIdentifier.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    static boolean excludes(Set<String> exclusions, Identifier id) {
        return exclusions.contains(id.getNamespace()) || exclusions.contains(id.toString());
    }
}

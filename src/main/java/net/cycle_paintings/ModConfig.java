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
        return loadResult(path).config();
    }

    record LoadResult(ModConfig config, boolean success) {}

    static synchronized LoadResult loadResult(Path path) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, TEMPLATE, StandardOpenOption.CREATE_NEW);
                return new LoadResult(DEFAULT, true);
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
                return new LoadResult(new ModConfig(ignored, debug), true);
            }
        } catch (IOException | RuntimeException exception) {
            CyclePaintings.LOGGER.warn("[Cycle Paintings] Could not load config {}. Existing files are preserved.",
                    path, exception);
            return new LoadResult(DEFAULT, false);
        }
    }

    static synchronized ModConfig forEditing(Path path) throws IOException {
        var result = loadResult(path);
        if (!result.success()) throw new IOException("Cannot read configuration. Existing file preserved.");
        return result.config();
    }

    static synchronized void save(Path path, ModConfig edited) throws IOException {
        if (edited.ignorePaintingsInCycleIdentifier() == null)
            throw new IllegalArgumentException("The painting exclusion list must be a string.");
        JsonObject json = new JsonObject();
        if (Files.exists(path)) {
            // Reject invalid existing data; never silently replace a broken config from the GUI.
            forEditing(path);
            json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        }
        json.addProperty("ignorePaintingsInCycleIdentifier", edited.ignorePaintingsInCycleIdentifier());
        json.addProperty("showRegisteredPaintingsDebug", edited.showRegisteredPaintingsDebug());
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "cycle_paintings-", ".tmp");
        try {
            Files.writeString(temporary, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
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

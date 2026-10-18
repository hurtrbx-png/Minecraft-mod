package io.github.hurtrbxpng.giantess.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.hurtrbxpng.giantess.Giantess;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All tunable values live here and are read from {@code config/giantess.json}.
 * Every milestone adds its own keys; nothing tunable is hardcoded elsewhere.
 */
public final class GiantessConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "giantess.json";

	private static GiantessConfig instance = new GiantessConfig();

	/** Bumped when a key is renamed or removed, so old files can be migrated. */
	public int configVersion = 1;
	/** Extra diagnostic logging for development. */
	public boolean debugLogging = false;

	public static GiantessConfig get() {
		return instance;
	}

	/**
	 * Reads the config file, falling back to defaults if it is missing or unreadable.
	 * The file is rewritten afterwards so newly added keys appear with their defaults.
	 * A file that cannot be parsed is left untouched so the user can fix it.
	 */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		GiantessConfig loaded = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, GiantessConfig.class);
			} catch (IOException | JsonParseException e) {
				Giantess.LOGGER.error("Could not read {}; using defaults and leaving the file untouched", path, e);
				return;
			}
		}

		instance = loaded != null ? loaded : new GiantessConfig();
		save(path);
	}

	private static void save(Path path) {
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			Giantess.LOGGER.error("Could not write {}", path, e);
		}
	}
}

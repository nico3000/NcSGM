package dev.nicotopia.ncsgm.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public record Configuration(String name, Path pathToWatch, Path backupFolder) {
    public static Map<String, Configuration> loadFromJsonResource(String resourcePath) throws IOException {
        JSONArray json;
        try (var is = Configuration.class.getResourceAsStream(resourcePath)) {
            json = new JSONArray(new String(is.readAllBytes()));
        } catch (JSONException ex) {
            return Collections.emptyMap();
        }
        return StreamSupport.stream(json.spliterator(), false).filter(o -> o instanceof JSONObject)
                .map(JSONObject.class::cast).map(Configuration::new)
                .collect(HashMap::new, (m, c) -> m.put(c.name(), c), HashMap::putAll);
    }

    private static Path parse(String pathStr) {
        pathStr = pathStr.replace("${home}", System.getProperty("user.home"));
        Path path = null;
        int pos = -1;
        while ((pos = pathStr.indexOf("*/")) != -1) {
            var next = pathStr.substring(0, pos);
            path = path == null ? Path.of(next) : next.isEmpty() ? path : path.resolve(next);
            var children = path.toFile().list((dir, name) -> new File(dir, name).isDirectory());
            path = path.resolve(children != null && children.length != 0 ? children[0] : "unknown");
            pathStr = pathStr.substring(pos + 2);
        }
        return path == null ? Path.of(pathStr) : pathStr.isEmpty() ? path : path.resolve(pathStr);
    }

    public Configuration(JSONObject json) {
        this(json.getString("name"), parse(json.getString("pathToWatch")), parse(json.getString("backupFolder")));
    }
}

package dev.nicotopia.ncsgm.model;

import java.awt.Image;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;

public class Backup implements Comparable<Backup> {
    public static Optional<Backup> createFromExistingFile(File file) {
        if (!file.isFile() || !file.canWrite() || !file.getName().endsWith(".zip")) {
            return Optional.empty();
        }
        try (var zip = new ZipFile(file, ZipFile.OPEN_READ)) {
            return Optional.ofNullable(zip.getComment()).map(JSONObject::new)
                    .filter(j -> "dossgm".equals(j.optString("origin")))
                    .map(j -> new Backup(file, j.getString("timestamp")));
        } catch (IOException | JSONException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Backup> createNew(Path path, String name, String timestamp) {
        var file = path.resolve(name + ".zip").toFile();
        return !file.exists() ? Optional.of(new Backup(file, timestamp)) : Optional.empty();
    }

    private File file;
    private final String timestamp;
    private Image image;
    private boolean active = false;
    private final List<PropertyChangeListener> listeners = new LinkedList<>();

    private Backup(File file, String timestamp) {
        this.file = file;
        this.timestamp = timestamp;
        try {
            this.updateImage();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String getName() {
        return this.file.getName().substring(0, this.file.getName().length() - 4);
    }

    public Image getImage() {
        return this.image;
    }

    public long getLastModified() {
        return this.file.lastModified();
    }

    public long getFileByteWidth() {
        return this.file.length();
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            PropertyChangeEvent evt = new PropertyChangeEvent(this, "active", !active, active);
            this.listeners.forEach(l -> l.propertyChange(evt));
        }
    }

    public boolean deleteFile() {
        return this.file.delete();
    }

    public boolean setName(String newName) {
        var newFile = this.file.toPath().getParent().resolve(newName + ".zip").toFile();
        if (newFile.exists() || !this.file.renameTo(newFile)) {
            return false;
        }
        PropertyChangeEvent evt = new PropertyChangeEvent(this, "filename", this.file.getName(), file.getName());
        this.file = newFile;
        this.listeners.forEach(l -> l.propertyChange(evt));
        return true;
    }

    public ZipOutputStream getOutputStream() throws IOException {
        JSONObject commentJson = new JSONObject();
        commentJson.put("origin", "dossgm");
        commentJson.put("timestamp", timestamp);
        var zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(this.file)));
        zos.setComment(commentJson.toString());
        return zos;
    }

    public ZipFile getZipFile() throws IOException {
        return new ZipFile(this.file);
    }

    public void updateImage() throws IOException {
        if (this.file.isFile()) {
            try (var zipFile = this.getZipFile()) {
                var imageEntry = zipFile.stream().filter(e -> e.getName().endsWith(".png")).findFirst();
                if (imageEntry.isPresent()) {
                    this.image = ImageIO.read(zipFile.getInputStream(imageEntry.get()));
                }
            }
        }
    }

    @Override
    public int compareTo(Backup o) {
        return this.timestamp.compareTo(o.timestamp);
    }

    @Override
    public String toString() {
        return this.getName() + (this.isActive() ? " *" : "");
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        this.listeners.add(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        this.listeners.remove(l);
    }
}
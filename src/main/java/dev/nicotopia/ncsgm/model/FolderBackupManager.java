package dev.nicotopia.ncsgm.model;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

public class FolderBackupManager implements ListModel<Backup>, PropertyChangeListener {
    public static class RenameFailedException extends Exception {
        RenameFailedException(String s) {
            super(s);
        }
    }

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String TIMESTAMP_REGEX = "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}";

    private final Configuration config;
    private final String backupNameRegex;
    private final int maxBackups;
    private final List<Backup> backups;
    private final List<ListDataListener> listeners = new LinkedList<>();

    public FolderBackupManager(Configuration config, int maxBackups) {
        var backupFolder = config.backupFolder().toFile();
        if (!backupFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid or non-existent backup folder given: " + backupFolder);
        }
        this.config = config;
        this.backupNameRegex = String.format("\\Q%s\\E_%s", this.config.pathToWatch().getFileName(), TIMESTAMP_REGEX);
        this.maxBackups = maxBackups;
        this.backups = Arrays.stream(backupFolder.listFiles()).map(Backup::createFromExistingFile)
                .filter(Optional::isPresent).map(Optional::get).sorted().collect(Collectors.toList());
        this.backups.stream().forEach(b -> b.addPropertyChangeListener(this));
    }

    public Configuration getConfiguration() {
        return this.config;
    }

    public int createBackup() throws IOException {
        String timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now());
        var name = String.format("%s_%s", this.config.pathToWatch().getFileName(), timestamp);
        var newBackup = Backup.createNew(this.config.backupFolder(), name, timestamp);
        if (!newBackup.isPresent()) {
            return -1;
        }
        System.out.printf("Backing up to %s...", newBackup.get());
        long beg = System.currentTimeMillis();
        try (ZipOutputStream zos = newBackup.get().getOutputStream()) {
            this.zip(zos, null, this.config.pathToWatch().toFile());
        }
        this.backups.forEach(backup -> backup.setActive(false));
        newBackup.get().setActive(true);
        this.backups.add(newBackup.get());
        var evt = new ListDataEvent(this, ListDataEvent.INTERVAL_ADDED, this.getSize() - 1, this.getSize() - 1);
        this.listeners.forEach(l -> l.intervalAdded(evt));
        this.ensureMaxBackupConstraint();
        newBackup.get().updateImage();
        newBackup.get().addPropertyChangeListener(this);
        System.out.printf("done (%d ms)\n", System.currentTimeMillis() - beg);
        return this.backups.indexOf(newBackup.get());
    }

    private void zip(ZipOutputStream zos, String baseName, File file) throws IOException {
        String entryName = (baseName != null ? baseName + "/" : "") + file.getName();
        if (file.isDirectory()) {
            ZipEntry entry = new ZipEntry(entryName + "/");
            zos.putNextEntry(entry);
            zos.closeEntry();
            for (File child : file.listFiles()) {
                zip(zos, entryName, child);
            }
        } else {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            try (FileInputStream fis = new FileInputStream(file)) {
                zos.write(fis.readAllBytes());
            }
            zos.closeEntry();
        }
    }

    public void restoreBackup(int idx) throws IOException {
        if (idx < 0 || this.backups.size() <= idx) {
            throw new IllegalArgumentException("Invalid backup index.");
        }
        var backup = this.backups.get(idx);
        var beg = System.currentTimeMillis();
        System.out.printf("Now restoring %s...", backup);
        this.deleteContents(this.config.pathToWatch().toFile());
        try (var zipFile = backup.getZipFile()) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                File file = this.config.pathToWatch().getParent().resolve(entry.getName()).toFile();
                if (!entry.isDirectory()) {
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                            var is = zipFile.getInputStream(entry)) {
                        bos.write(is.readAllBytes());
                    }
                } else if (!file.exists() && !file.mkdirs()) {
                    throw new IOException("\nFailed to create folder " + entry.getName());
                }
            }
        }
        this.backups.forEach(b -> b.setActive(backup == b));
        System.out.printf("done (%d ms)\n", System.currentTimeMillis() - beg);
    }

    public boolean deleteBackup(int idx) {
        var backup = this.backups.remove(idx);
        backup.removePropertyChangeListener(this);
        ListDataEvent evt = new ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, idx, idx);
        this.listeners.forEach(l -> l.intervalRemoved(evt));
        return backup.deleteFile();
    }

    public void renameBackup(int idx, String newName, Supplier<Boolean> forceOverwriteProvider)
            throws RenameFailedException {
        Backup backup = this.backups.get(idx);
        var existing = IntStream.range(0, this.backups.size())
                .filter(i -> this.backups.get(i).getName().equalsIgnoreCase(newName)).findAny();
        if (existing.isPresent()) {
            if (forceOverwriteProvider == null || !forceOverwriteProvider.get()) {
                return;
            }
            if (!this.deleteBackup(existing.getAsInt())) {
                throw new RenameFailedException("Deletion of previous backup failed.");
            }
        }
        try {
            if (!backup.setName(newName)) {
                throw new RenameFailedException("Rename failed.");
            }
        } catch (InvalidPathException ex) {
            throw new RenameFailedException(ex.getMessage());
        }
    }

    private boolean isManaged(Backup backup) {
        return backup.getName().matches(this.backupNameRegex);
    }

    private void ensureMaxBackupConstraint() {
        if (maxBackups < this.backups.stream().filter(this::isManaged).count()) {
            IntStream.range(0, this.backups.size()).filter(i -> this.isManaged(this.backups.get(i))).findFirst()
                    .ifPresent(this::deleteBackup);
        }
    }

    private void deleteContents(File dir) {
        if (dir.exists()) {
            for (File c : dir.listFiles()) {
                if (c.isDirectory()) {
                    this.deleteContents(c);
                }
                c.delete();
            }
        }
    }

    @Override
    public int getSize() {
        return (int) this.backups.stream().filter(f -> f != null).count();
    }

    @Override
    public Backup getElementAt(int index) {
        return index < this.backups.size() ? this.backups.get(index) : null;
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        this.listeners.add(l);
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        this.listeners.remove(l);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        int idx = this.backups.indexOf(evt.getSource());
        var e = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, idx, idx);
        this.listeners.forEach(l -> l.contentsChanged(e));
    }
}

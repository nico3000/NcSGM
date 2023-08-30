package dev.nicotopia.ncsgm.model;

import java.nio.file.Path;

public class FolderWatcher {
    private final Path folderPath;
    private final Thread thread;
    private Runnable onModifiedCallback;
    private long lastModified = -1;

    public FolderWatcher(Path folderPath) {
        this.folderPath = folderPath;
        this.thread = new Thread(this::run);
    }

    private void run() {
        System.out.printf("Now watching for changes: %s\n", this.folderPath.toFile().getAbsolutePath());
        this.updateLastModified();
        try {
            while (!this.thread.isInterrupted()) {
                Thread.sleep(1000);
                this.checkFolder();
            }
        } catch (InterruptedException ex) {
        }
    }

    public void start(Runnable onModifiedCallback) {
        this.onModifiedCallback = onModifiedCallback;
        this.thread.start();
    }

    public void interrupt(boolean join) {
        this.thread.interrupt();
        if (join) {
            try {
                this.thread.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void checkFolder() {
        synchronized (this.folderPath) {
            var folder = this.folderPath.toFile();
            if (folder.isDirectory() && this.lastModified != folder.lastModified()
                    && folder.lastModified() + 1000 < System.currentTimeMillis()) {
                if (this.onModifiedCallback != null) {
                    this.onModifiedCallback.run();
                }
                this.lastModified = folder.lastModified();
            }
        }
    }

    public void updateLastModified() {
        synchronized (this.folderPath) {
            this.lastModified = this.folderPath.toFile().lastModified();
        }
    }
}

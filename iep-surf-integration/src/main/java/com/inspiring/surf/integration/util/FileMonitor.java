package com.inspiring.surf.integration.util;

import com.inspiring.surf.integration.listeners.FileListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

public class FileMonitor {

    private static final FileMonitor instance = new FileMonitor();

    private Timer timer;

    private Hashtable<String, FileMonitorTask> timerEntries;

    public static FileMonitor getInstance() {
        return instance;
    }

    private FileMonitor() {
        // Create timer, run timer thread as daemon.
        timer = new Timer("FileMonitor", true);
        timerEntries = new Hashtable<String, FileMonitorTask>();
    }

    public void addFileListener(FileListener listener,
                                String fileName, long period) throws FileNotFoundException {
        addFileListener(listener, new File(fileName), period);
    }

    public void addFileListener(FileListener listener, File file,
                                long period) throws FileNotFoundException {
        removeFileListener(listener, file);
        FileMonitorTask task = new FileMonitorTask(listener, file);
        timerEntries.put(file.toString() + listener.hashCode(), task);
        timer.schedule(task, period, period);
    }

    public void removeFileListener(FileListener listener, String fileName) {
        removeFileListener(listener, new File(fileName));
    }

    public void removeFileListener(FileListener listener, File file) {
        FileMonitorTask task = timerEntries.remove(file.toString() + listener.hashCode());
        if (task != null) {
            task.cancel();
        }
    }

    protected void fireFileChangeEvent(FileListener listener, File file) {
        listener.fileChanged(file);
    }

    /**
     * File monitoring task.
     */
    class FileMonitorTask extends TimerTask {

        FileListener listener;

        File monitoredFile;

        long lastModified;

        public FileMonitorTask(FileListener listener, File file) throws FileNotFoundException {
            this.listener = listener;
            this.lastModified = 0;
            monitoredFile = file;
            if (!monitoredFile.exists()) { // but is it on CLASSPATH?
                URL fileURL = listener.getClass().getClassLoader().getResource(file.toString());
                if (fileURL != null) {
                    monitoredFile = new File(fileURL.getFile());
                } else {
                    throw new FileNotFoundException("File Not Found: " + file);
                }
            }
            this.lastModified = monitoredFile.lastModified();
        }

        public void run() {
            long lastModified = monitoredFile.lastModified();
            if (lastModified != this.lastModified) {
                this.lastModified = lastModified;
                fireFileChangeEvent(this.listener, monitoredFile);
            }
        }
    }
}

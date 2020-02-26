package com.inspiring.surf.integration.listeners;

import java.io.File;

public interface FileListener {

    /**
     * Invoked when a file changes.
     *
     * @param fileName name of changed file.
     */
    public void fileChanged(File fileName);

}

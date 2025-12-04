package com.backupforce;

import com.backupforce.ui.BackupForceApp;

/**
 * Launcher class to avoid JavaFX module issues when running as executable JAR.
 * This class doesn't extend Application, which allows the JAR to run without
 * --module-path arguments.
 */
public class Launcher {
    public static void main(String[] args) {
        BackupForceApp.main(args);
    }
}

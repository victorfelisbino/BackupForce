package com.backupforce.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Base class for JavaFX UI tests using TestFX.
 * Provides common utilities for testing JavaFX components.
 * 
 * NOTE: Headless mode is configured via Maven Surefire plugin argLine.
 * Tests run headless by default, so you can use your computer while tests run.
 */
public abstract class JavaFxTestBase extends ApplicationTest {
    
    protected Stage testStage;
    
    @BeforeAll
    public static void setupHeadless() {
        // Enable headless mode for CI/CD environments
        if (Boolean.getBoolean("headless")) {
            System.setProperty("testfx.robot", "glass");
            System.setProperty("testfx.headless", "true");
            System.setProperty("prism.order", "sw");
            System.setProperty("prism.text", "t2k");
            System.setProperty("java.awt.headless", "true");
        }
    }
    
    @Override
    public void start(Stage stage) throws Exception {
        this.testStage = stage;
        stage.setScene(createTestScene());
        stage.show();
        stage.toFront();
    }
    
    /**
     * Subclasses implement this to provide their test scene.
     */
    protected abstract Scene createTestScene() throws Exception;
    
    // ==================== Utility Methods ====================
    
    /**
     * Wait for the JavaFX thread to complete pending operations.
     */
    protected void waitForFxEvents() {
        WaitForAsyncUtils.waitForFxEvents();
    }
    
    /**
     * Wait for a condition to become true with timeout.
     */
    protected void waitUntil(Supplier<Boolean> condition, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        while (!condition.get()) {
            if (System.currentTimeMillis() - start > timeoutSeconds * 1000L) {
                throw new RuntimeException("Timeout waiting for condition");
            }
            waitForFxEvents();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Run an action on the JavaFX thread and wait for completion.
     */
    protected void runOnFxThread(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        waitForFxEvents();
    }
    
    /**
     * Get a node by its fx:id.
     */
    @SuppressWarnings("unchecked")
    protected <T extends Node> T findById(String fxId) {
        return (T) lookup("#" + fxId).query();
    }
    
    /**
     * Get text from a labeled control.
     */
    protected String getTextFrom(String fxId) {
        Node node = findById(fxId);
        if (node instanceof TextField) {
            return ((TextField) node).getText();
        } else if (node instanceof TextArea) {
            return ((TextArea) node).getText();
        } else if (node instanceof Label) {
            return ((Label) node).getText();
        } else if (node instanceof Labeled) {
            return ((Labeled) node).getText();
        }
        throw new IllegalArgumentException("Node is not a text container: " + fxId);
    }
    
    /**
     * Set text on a text field.
     */
    protected void setTextOn(String fxId, String text) {
        clickOn("#" + fxId);
        TextField field = findById(fxId);
        runOnFxThread(() -> {
            field.clear();
            field.setText(text);
        });
        waitForFxEvents();
    }
    
    /**
     * Check if a node is visible.
     */
    protected boolean isVisible(String fxId) {
        Node node = findById(fxId);
        return node != null && node.isVisible() && node.isManaged();
    }
    
    /**
     * Check if a button is disabled.
     */
    protected boolean isDisabled(String fxId) {
        Node node = findById(fxId);
        return node != null && node.isDisabled();
    }
    
    /**
     * Select an item in a ComboBox.
     */
    protected <T> void selectInComboBox(String fxId, int index) {
        ComboBox<T> combo = findById(fxId);
        runOnFxThread(() -> combo.getSelectionModel().select(index));
        waitForFxEvents();
    }
    
    /**
     * Get selected item from ComboBox.
     */
    protected <T> T getSelectedFromComboBox(String fxId) {
        ComboBox<T> combo = findById(fxId);
        return combo.getSelectionModel().getSelectedItem();
    }
    
    /**
     * Check a CheckBox.
     */
    protected void setCheckBox(String fxId, boolean checked) {
        CheckBox checkBox = findById(fxId);
        runOnFxThread(() -> checkBox.setSelected(checked));
        waitForFxEvents();
    }
    
    /**
     * Check if CheckBox is selected.
     */
    protected boolean isCheckBoxSelected(String fxId) {
        CheckBox checkBox = findById(fxId);
        return checkBox.isSelected();
    }
    
    /**
     * Get row count from a TableView.
     */
    protected int getTableRowCount(String fxId) {
        TableView<?> table = findById(fxId);
        return table.getItems().size();
    }
    
    /**
     * Get progress from a ProgressBar.
     */
    protected double getProgress(String fxId) {
        ProgressBar progressBar = findById(fxId);
        return progressBar.getProgress();
    }
    
    /**
     * Verify a node exists and is visible.
     */
    protected void assertNodeVisible(String fxId, String message) {
        Node node = findById(fxId);
        if (node == null) {
            throw new AssertionError(message + ": Node not found with id " + fxId);
        }
        if (!node.isVisible()) {
            throw new AssertionError(message + ": Node is not visible - " + fxId);
        }
    }
    
    /**
     * Verify a node is not visible or doesn't exist.
     */
    protected void assertNodeNotVisible(String fxId, String message) {
        Node node = findById(fxId);
        if (node != null && node.isVisible() && node.isManaged()) {
            throw new AssertionError(message + ": Node should not be visible - " + fxId);
        }
    }
}

package com.backupforce.ui;

import com.backupforce.scheduler.BackupSchedule;
import com.backupforce.scheduler.BackupSchedule.Frequency;
import com.backupforce.scheduler.BackupSchedulerService;
import com.backupforce.scheduler.ScheduleManager;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Controller for the Schedule Management UI.
 * Allows users to create, edit, and manage scheduled backups.
 */
public class ScheduleController {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduleController.class);
    
    @FXML private TableView<ScheduleItem> schedulesTable;
    @FXML private TableColumn<ScheduleItem, Boolean> enabledColumn;
    @FXML private TableColumn<ScheduleItem, String> nameColumn;
    @FXML private TableColumn<ScheduleItem, String> frequencyColumn;
    @FXML private TableColumn<ScheduleItem, String> nextRunColumn;
    @FXML private TableColumn<ScheduleItem, String> lastRunColumn;
    @FXML private TableColumn<ScheduleItem, String> statusColumn;
    
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button runNowButton;
    @FXML private Label schedulerStatusLabel;
    @FXML private ToggleButton schedulerToggle;
    
    private final ObservableList<ScheduleItem> scheduleItems = FXCollections.observableArrayList();
    private final ScheduleManager scheduleManager = ScheduleManager.getInstance();
    private final BackupSchedulerService schedulerService = BackupSchedulerService.getInstance();
    
    // For creating schedules from current backup selection
    private List<String> currentSelectedObjects = new ArrayList<>();
    private String currentSalesforceUsername;
    private String currentInstanceUrl;
    private boolean currentUseSandbox;
    private String currentOutputFolder;
    
    @FXML
    public void initialize() {
        setupTable();
        loadSchedules();
        updateSchedulerStatus();
        
        // Disable edit/delete/run when nothing selected
        editButton.disableProperty().bind(
            schedulesTable.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(
            schedulesTable.getSelectionModel().selectedItemProperty().isNull());
        runNowButton.disableProperty().bind(
            schedulesTable.getSelectionModel().selectedItemProperty().isNull());
    }
    
    private void setupTable() {
        // Enabled column with checkbox
        enabledColumn.setCellValueFactory(cd -> cd.getValue().enabledProperty());
        enabledColumn.setCellFactory(col -> new CheckBoxTableCell<>());
        
        // Name column
        nameColumn.setCellValueFactory(cd -> cd.getValue().nameProperty());
        
        // Frequency column
        frequencyColumn.setCellValueFactory(cd -> cd.getValue().frequencyProperty());
        
        // Next run column
        nextRunColumn.setCellValueFactory(cd -> cd.getValue().nextRunProperty());
        
        // Last run column  
        lastRunColumn.setCellValueFactory(cd -> cd.getValue().lastRunProperty());
        
        // Status column with color coding
        statusColumn.setCellValueFactory(cd -> cd.getValue().statusProperty());
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("SUCCESS")) {
                        setStyle("-fx-text-fill: #4CAF50;");
                    } else if (item.startsWith("FAILED")) {
                        setStyle("-fx-text-fill: #f44336;");
                    } else if (item.equals("Disabled")) {
                        setStyle("-fx-text-fill: #9E9E9E;");
                    } else {
                        setStyle("-fx-text-fill: #2196F3;");
                    }
                }
            }
        });
        
        schedulesTable.setItems(scheduleItems);
        schedulesTable.setPlaceholder(new Label("No schedules configured.\nClick 'Add Schedule' to create one."));
    }
    
    private void loadSchedules() {
        scheduleItems.clear();
        for (BackupSchedule schedule : scheduleManager.getSchedules()) {
            scheduleItems.add(new ScheduleItem(schedule));
        }
    }
    
    private void updateSchedulerStatus() {
        boolean running = schedulerService.isRunning();
        schedulerToggle.setSelected(running);
        schedulerToggle.setText(running ? "Scheduler ON" : "Scheduler OFF");
        
        int count = schedulerService.getScheduledCount();
        schedulerStatusLabel.setText(running 
            ? String.format("%d schedule(s) active", count)
            : "Scheduler is stopped");
    }
    
    @FXML
    private void handleToggleScheduler() {
        if (schedulerToggle.isSelected()) {
            schedulerService.start();
        } else {
            schedulerService.stop();
        }
        updateSchedulerStatus();
    }
    
    @FXML
    private void handleAddSchedule() {
        showScheduleEditor(null);
    }
    
    @FXML
    private void handleEditSchedule() {
        ScheduleItem selected = schedulesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showScheduleEditor(selected.getSchedule());
        }
    }
    
    @FXML
    private void handleDeleteSchedule() {
        ScheduleItem selected = schedulesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Schedule");
        confirm.setHeaderText("Delete \"" + selected.getName() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                scheduleManager.deleteSchedule(selected.getSchedule().getId());
                schedulerService.cancelSchedule(selected.getSchedule().getId());
                loadSchedules();
                updateSchedulerStatus();
            }
        });
    }
    
    @FXML
    private void handleRunNow() {
        ScheduleItem selected = schedulesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            schedulerService.runNow(selected.getSchedule().getId());
            showInfo("Backup Started", "Running backup: " + selected.getName());
        }
    }
    
    /**
     * Create a schedule from the current backup selection.
     */
    public void createScheduleFromCurrentSelection(List<String> objects, String username, 
            String instanceUrl, boolean sandbox, String outputFolder) {
        this.currentSelectedObjects = new ArrayList<>(objects);
        this.currentSalesforceUsername = username;
        this.currentInstanceUrl = instanceUrl;
        this.currentUseSandbox = sandbox;
        this.currentOutputFolder = outputFolder;
        
        BackupSchedule schedule = new BackupSchedule("New Schedule");
        schedule.setSelectedObjects(currentSelectedObjects);
        schedule.setSalesforceUsername(currentSalesforceUsername);
        schedule.setSalesforceInstanceUrl(currentInstanceUrl);
        schedule.setUseSandbox(currentUseSandbox);
        schedule.setOutputFolder(currentOutputFolder);
        
        showScheduleEditor(schedule);
    }
    
    /**
     * Show the schedule editor dialog.
     */
    private void showScheduleEditor(BackupSchedule schedule) {
        boolean isNew = (schedule == null);
        BackupSchedule editSchedule = isNew ? new BackupSchedule("New Schedule") : schedule;
        
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isNew ? "Create Schedule" : "Edit Schedule");
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1e1e1e;");
        
        // Schedule Name
        HBox nameRow = createFormRow("Schedule Name:", 
            createTextField(editSchedule.getName(), editSchedule::setName));
        
        // Frequency
        ComboBox<Frequency> frequencyCombo = new ComboBox<>();
        frequencyCombo.getItems().addAll(Frequency.values());
        frequencyCombo.setValue(editSchedule.getFrequency());
        frequencyCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Frequency f) { return f != null ? f.getDisplayName() : ""; }
            @Override
            public Frequency fromString(String s) { return null; }
        });
        HBox freqRow = createFormRow("Frequency:", frequencyCombo);
        
        // Time picker
        ComboBox<Integer> hourCombo = new ComboBox<>();
        hourCombo.getItems().addAll(IntStream.range(0, 24).boxed().collect(Collectors.toList()));
        hourCombo.setValue(editSchedule.getScheduledTime().getHour());
        
        ComboBox<Integer> minuteCombo = new ComboBox<>();
        minuteCombo.getItems().addAll(IntStream.range(0, 60).filter(m -> m % 15 == 0).boxed().collect(Collectors.toList()));
        minuteCombo.setValue(editSchedule.getScheduledTime().getMinute());
        
        HBox timeBox = new HBox(5);
        timeBox.getChildren().addAll(hourCombo, new Label(":"), minuteCombo);
        HBox timeRow = createFormRow("Time:", timeBox);
        
        // Days of week (for weekly)
        VBox daysBox = new VBox(5);
        Map<DayOfWeek, CheckBox> dayCheckboxes = new LinkedHashMap<>();
        HBox daysRow1 = new HBox(10);
        HBox daysRow2 = new HBox(10);
        for (DayOfWeek day : DayOfWeek.values()) {
            CheckBox cb = new CheckBox(day.toString().substring(0, 3));
            cb.setSelected(editSchedule.getDaysOfWeek().contains(day));
            cb.setStyle("-fx-text-fill: #cccccc;");
            dayCheckboxes.put(day, cb);
            if (day.getValue() <= 4) {
                daysRow1.getChildren().add(cb);
            } else {
                daysRow2.getChildren().add(cb);
            }
        }
        daysBox.getChildren().addAll(daysRow1, daysRow2);
        HBox daysRow = createFormRow("Days:", daysBox);
        daysRow.setVisible(editSchedule.getFrequency() == Frequency.WEEKLY);
        daysRow.setManaged(editSchedule.getFrequency() == Frequency.WEEKLY);
        
        // Day of month (for monthly)
        ComboBox<Integer> dayOfMonthCombo = new ComboBox<>();
        dayOfMonthCombo.getItems().add(0); // Last day
        dayOfMonthCombo.getItems().addAll(IntStream.rangeClosed(1, 31).boxed().collect(Collectors.toList()));
        dayOfMonthCombo.setValue(editSchedule.getDayOfMonth());
        dayOfMonthCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer d) { return d == 0 ? "Last day" : String.valueOf(d); }
            @Override
            public Integer fromString(String s) { return null; }
        });
        HBox domRow = createFormRow("Day of Month:", dayOfMonthCombo);
        domRow.setVisible(editSchedule.getFrequency() == Frequency.MONTHLY);
        domRow.setManaged(editSchedule.getFrequency() == Frequency.MONTHLY);
        
        // Update visibility based on frequency
        frequencyCombo.setOnAction(e -> {
            Frequency f = frequencyCombo.getValue();
            daysRow.setVisible(f == Frequency.WEEKLY);
            daysRow.setManaged(f == Frequency.WEEKLY);
            domRow.setVisible(f == Frequency.MONTHLY);
            domRow.setManaged(f == Frequency.MONTHLY);
        });
        
        // Output folder
        TextField folderField = new TextField(editSchedule.getOutputFolder() != null 
            ? editSchedule.getOutputFolder() 
            : System.getProperty("user.home") + File.separator + "BackupForce");
        folderField.setPrefWidth(300);
        Button browseBtn = new Button("Browse");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Folder");
            File dir = dc.showDialog(dialog);
            if (dir != null) {
                folderField.setText(dir.getAbsolutePath());
            }
        });
        HBox folderBox = new HBox(10, folderField, browseBtn);
        HBox folderRow = createFormRow("Output Folder:", folderBox);
        
        // Backup options
        CheckBox incrementalCb = new CheckBox("Incremental backup");
        incrementalCb.setSelected(editSchedule.isIncremental());
        incrementalCb.setStyle("-fx-text-fill: #cccccc;");
        
        CheckBox compressCb = new CheckBox("Compress to ZIP");
        compressCb.setSelected(editSchedule.isCompress());
        compressCb.setStyle("-fx-text-fill: #cccccc;");
        
        CheckBox preserveRelCb = new CheckBox("Preserve relationships");
        preserveRelCb.setSelected(editSchedule.isPreserveRelationships());
        preserveRelCb.setStyle("-fx-text-fill: #cccccc;");
        
        VBox optionsBox = new VBox(8, incrementalCb, compressCb, preserveRelCb);
        HBox optionsRow = createFormRow("Options:", optionsBox);
        
        // Objects info
        int objectCount = editSchedule.getSelectedObjects() != null 
            ? editSchedule.getSelectedObjects().size() : 0;
        Label objectsLabel = new Label(objectCount + " object(s) selected");
        objectsLabel.setStyle("-fx-text-fill: #cccccc;");
        HBox objectsRow = createFormRow("Objects:", objectsLabel);
        
        // Enable/disable toggle
        CheckBox enabledCb = new CheckBox("Schedule enabled");
        enabledCb.setSelected(editSchedule.isEnabled());
        enabledCb.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold;");
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(15, 0, 0, 0));
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> dialog.close());
        
        Button saveBtn = new Button(isNew ? "Create" : "Save");
        saveBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> {
            // Collect values
            editSchedule.setFrequency(frequencyCombo.getValue());
            editSchedule.setScheduledTime(LocalTime.of(hourCombo.getValue(), minuteCombo.getValue()));
            editSchedule.setDaysOfWeek(dayCheckboxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));
            editSchedule.setDayOfMonth(dayOfMonthCombo.getValue());
            editSchedule.setOutputFolder(folderField.getText());
            editSchedule.setIncremental(incrementalCb.isSelected());
            editSchedule.setCompress(compressCb.isSelected());
            editSchedule.setPreserveRelationships(preserveRelCb.isSelected());
            editSchedule.setEnabled(enabledCb.isSelected());
            
            // Save
            if (isNew) {
                scheduleManager.addSchedule(editSchedule);
            } else {
                scheduleManager.updateSchedule(editSchedule);
            }
            
            // Reschedule
            schedulerService.reschedule(editSchedule);
            
            loadSchedules();
            updateSchedulerStatus();
            dialog.close();
        });
        
        buttonBox.getChildren().addAll(cancelBtn, saveBtn);
        
        // Layout
        root.getChildren().addAll(
            createSectionLabel("Schedule Details"),
            nameRow, freqRow, timeRow, daysRow, domRow,
            new Separator(),
            createSectionLabel("Backup Settings"),
            folderRow, optionsRow, objectsRow,
            new Separator(),
            enabledCb,
            buttonBox
        );
        
        Scene scene = new Scene(root, 500, 550);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
    
    private HBox createFormRow(String label, javafx.scene.Node control) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #cccccc;");
        lbl.setMinWidth(120);
        row.getChildren().addAll(lbl, control);
        return row;
    }
    
    private TextField createTextField(String value, java.util.function.Consumer<String> setter) {
        TextField tf = new TextField(value);
        tf.setPrefWidth(250);
        tf.textProperty().addListener((obs, o, n) -> setter.accept(n));
        return tf;
    }
    
    private Label createSectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 14px;");
        return label;
    }
    
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // ==================== ScheduleItem (Table Model) ====================
    
    public static class ScheduleItem {
        private final BackupSchedule schedule;
        private final BooleanProperty enabled;
        private final StringProperty name;
        private final StringProperty frequency;
        private final StringProperty nextRun;
        private final StringProperty lastRun;
        private final StringProperty status;
        
        public ScheduleItem(BackupSchedule schedule) {
            this.schedule = schedule;
            this.enabled = new SimpleBooleanProperty(schedule.isEnabled());
            this.name = new SimpleStringProperty(schedule.getName());
            this.frequency = new SimpleStringProperty(schedule.getScheduleDescription());
            this.nextRun = new SimpleStringProperty(formatNextRun(schedule));
            this.lastRun = new SimpleStringProperty(formatLastRun(schedule));
            this.status = new SimpleStringProperty(schedule.getStatusString());
            
            // Listen for enable toggle
            this.enabled.addListener((obs, o, n) -> {
                schedule.setEnabled(n);
                ScheduleManager.getInstance().updateSchedule(schedule);
                BackupSchedulerService.getInstance().reschedule(schedule);
                status.set(schedule.getStatusString());
            });
        }
        
        public BackupSchedule getSchedule() { return schedule; }
        
        public BooleanProperty enabledProperty() { return enabled; }
        public StringProperty nameProperty() { return name; }
        public StringProperty frequencyProperty() { return frequency; }
        public StringProperty nextRunProperty() { return nextRun; }
        public StringProperty lastRunProperty() { return lastRun; }
        public StringProperty statusProperty() { return status; }
        
        public String getName() { return name.get(); }
        
        private String formatNextRun(BackupSchedule s) {
            if (!s.isEnabled()) return "-";
            return BackupSchedulerService.getInstance().getNextRunTimeFormatted(s);
        }
        
        private String formatLastRun(BackupSchedule s) {
            if (s.getLastRunTime() == 0) return "Never";
            return java.time.Instant.ofEpochMilli(s.getLastRunTime())
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        }
    }
}

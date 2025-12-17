package com.backupforce.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.beans.property.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dialog for selecting which fields to include in a backup for a specific Salesforce object.
 * Allows users to choose specific fields instead of backing up all fields.
 */
public class FieldSelectionDialog extends Dialog<Set<String>> {
    private static final Logger logger = LoggerFactory.getLogger(FieldSelectionDialog.class);
    
    private final String objectName;
    private final String instanceUrl;
    private final String accessToken;
    private final String apiVersion;
    private final Set<String> preselectedFields;
    
    private ObservableList<FieldItem> allFields;
    private FilteredList<FieldItem> filteredFields;
    private TableView<FieldItem> fieldTable;
    private TextField searchField;
    private Label statusLabel;
    private Label selectionCountLabel;
    private ProgressIndicator loadingIndicator;
    
    /**
     * Creates a new field selection dialog.
     * 
     * @param objectName The Salesforce object name (e.g., "Account", "Contact")
     * @param instanceUrl The Salesforce instance URL
     * @param accessToken The Salesforce access token
     * @param apiVersion The Salesforce API version
     * @param preselectedFields Previously selected fields (null = all fields)
     */
    public FieldSelectionDialog(String objectName, String instanceUrl, String accessToken, 
                                String apiVersion, Set<String> preselectedFields) {
        this.objectName = objectName;
        this.instanceUrl = instanceUrl;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
        this.preselectedFields = preselectedFields;
        
        setTitle("Select Fields - " + objectName);
        setHeaderText("Choose which fields to include in the backup for " + objectName);
        initModality(Modality.APPLICATION_MODAL);
        
        // Set up dialog size
        getDialogPane().setPrefSize(700, 550);
        
        // Create content
        VBox content = createContent();
        getDialogPane().setContent(content);
        
        // Add buttons
        ButtonType selectAllType = new ButtonType("Select All", ButtonBar.ButtonData.LEFT);
        ButtonType deselectAllType = new ButtonType("Deselect All", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(selectAllType, deselectAllType, ButtonType.OK, ButtonType.CANCEL);
        
        // Handle Select All / Deselect All
        Button selectAllBtn = (Button) getDialogPane().lookupButton(selectAllType);
        Button deselectAllBtn = (Button) getDialogPane().lookupButton(deselectAllType);
        
        selectAllBtn.setOnAction(e -> {
            e.consume();
            allFields.forEach(f -> f.setSelected(true));
            updateSelectionCount();
        });
        
        deselectAllBtn.setOnAction(e -> {
            e.consume();
            allFields.forEach(f -> {
                if (!f.isRequired()) {
                    f.setSelected(false);
                }
            });
            updateSelectionCount();
        });
        
        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return allFields.stream()
                    .filter(FieldItem::isSelected)
                    .map(FieldItem::getName)
                    .collect(Collectors.toSet());
            }
            return null;
        });
        
        // Load fields
        loadFields();
        
        // Apply dark theme styling
        getDialogPane().getStylesheets().add(getClass().getResource("/css/windows11-dark.css").toExternalForm());
    }
    
    private VBox createContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        
        // Search bar
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchField = new TextField();
        searchField.setPromptText("Search fields...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterFields(newVal));
        
        selectionCountLabel = new Label("0 of 0 fields selected");
        selectionCountLabel.setStyle("-fx-font-weight: bold;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        searchBox.getChildren().addAll(searchField, spacer, selectionCountLabel);
        
        // Field table
        fieldTable = new TableView<>();
        fieldTable.setEditable(true);
        VBox.setVgrow(fieldTable, Priority.ALWAYS);
        
        // Checkbox column
        TableColumn<FieldItem, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(col -> new CheckBoxTableCell<FieldItem, Boolean>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    FieldItem fieldItem = getTableView().getItems().get(getIndex());
                    if (fieldItem.isRequired()) {
                        setDisable(true);
                        setStyle("-fx-opacity: 0.5;");
                    } else {
                        setDisable(false);
                        setStyle("");
                    }
                }
            }
        });
        selectCol.setPrefWidth(60);
        selectCol.setEditable(true);
        
        // Name column
        TableColumn<FieldItem, String> nameCol = new TableColumn<>("API Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        nameCol.setPrefWidth(180);
        
        // Label column
        TableColumn<FieldItem, String> labelCol = new TableColumn<>("Label");
        labelCol.setCellValueFactory(cellData -> cellData.getValue().labelProperty());
        labelCol.setPrefWidth(180);
        
        // Type column
        TableColumn<FieldItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        typeCol.setPrefWidth(100);
        
        // Info column (shows required, custom, etc.)
        TableColumn<FieldItem, String> infoCol = new TableColumn<>("Info");
        infoCol.setCellValueFactory(cellData -> cellData.getValue().infoProperty());
        infoCol.setPrefWidth(120);
        
        fieldTable.getColumns().addAll(selectCol, nameCol, labelCol, typeCol, infoCol);
        
        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        
        StackPane tableContainer = new StackPane();
        tableContainer.getChildren().addAll(fieldTable, loadingIndicator);
        VBox.setVgrow(tableContainer, Priority.ALWAYS);
        
        // Status label
        statusLabel = new Label("Loading fields...");
        statusLabel.setStyle("-fx-font-style: italic;");
        
        // Quick filter buttons
        HBox filterBox = new HBox(10);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        Button standardFieldsBtn = new Button("Standard Fields");
        standardFieldsBtn.setOnAction(e -> {
            allFields.forEach(f -> f.setSelected(!f.isCustom() || f.isRequired()));
            updateSelectionCount();
        });
        
        Button customFieldsBtn = new Button("Custom Fields Only");
        customFieldsBtn.setOnAction(e -> {
            allFields.forEach(f -> f.setSelected(f.isCustom() || f.isRequired()));
            updateSelectionCount();
        });
        
        Button essentialFieldsBtn = new Button("Essential Only");
        essentialFieldsBtn.setTooltip(new Tooltip("Id, Name, CreatedDate, LastModifiedDate, and required fields"));
        essentialFieldsBtn.setOnAction(e -> {
            Set<String> essential = Set.of("Id", "Name", "CreatedDate", "LastModifiedDate", "CreatedById", "LastModifiedById", "OwnerId");
            allFields.forEach(f -> f.setSelected(essential.contains(f.getName()) || f.isRequired()));
            updateSelectionCount();
        });
        
        Label filterLabel = new Label("Quick Select:");
        filterBox.getChildren().addAll(filterLabel, standardFieldsBtn, customFieldsBtn, essentialFieldsBtn);
        
        content.getChildren().addAll(searchBox, tableContainer, filterBox, statusLabel);
        
        return content;
    }
    
    private void filterFields(String searchText) {
        if (filteredFields == null) return;
        
        if (searchText == null || searchText.isEmpty()) {
            filteredFields.setPredicate(f -> true);
        } else {
            String lowerSearch = searchText.toLowerCase();
            filteredFields.setPredicate(f -> 
                f.getName().toLowerCase().contains(lowerSearch) ||
                f.getLabel().toLowerCase().contains(lowerSearch) ||
                f.getType().toLowerCase().contains(lowerSearch)
            );
        }
    }
    
    private void loadFields() {
        loadingIndicator.setVisible(true);
        fieldTable.setDisable(true);
        
        Task<List<FieldItem>> loadTask = new Task<>() {
            @Override
            protected List<FieldItem> call() throws Exception {
                return fetchObjectFields();
            }
        };
        
        loadTask.setOnSucceeded(e -> {
            List<FieldItem> fields = loadTask.getValue();
            allFields = FXCollections.observableArrayList(fields);
            filteredFields = new FilteredList<>(allFields, f -> true);
            fieldTable.setItems(filteredFields);
            
            loadingIndicator.setVisible(false);
            fieldTable.setDisable(false);
            
            statusLabel.setText(String.format("Loaded %d fields (%d queryable)", 
                fields.size(), 
                fields.stream().filter(f -> !f.isExcluded()).count()));
            
            updateSelectionCount();
            
            // Add listener to update count when selections change
            allFields.forEach(f -> f.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectionCount()));
        });
        
        loadTask.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            statusLabel.setText("Failed to load fields: " + loadTask.getException().getMessage());
            logger.error("Failed to load fields for " + objectName, loadTask.getException());
        });
        
        new Thread(loadTask).start();
    }
    
    private void updateSelectionCount() {
        if (allFields == null) return;
        
        long selected = allFields.stream().filter(FieldItem::isSelected).count();
        long total = allFields.stream().filter(f -> !f.isExcluded()).count();
        selectionCountLabel.setText(String.format("%d of %d fields selected", selected, total));
    }
    
    private List<FieldItem> fetchObjectFields() throws Exception {
        String url = String.format("%s/services/data/v%s/sobjects/%s/describe", 
            instanceUrl, apiVersion, objectName);
        
        List<FieldItem> fields = new ArrayList<>();
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.setHeader("Authorization", "Bearer " + accessToken);
            
            try (ClassicHttpResponse response = httpClient.executeOpen(null, get, null)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() >= 400) {
                    throw new RuntimeException("Failed to describe object: " + responseBody);
                }
                
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                JsonArray fieldsArray = json.getAsJsonArray("fields");
                
                for (int i = 0; i < fieldsArray.size(); i++) {
                    JsonObject fieldJson = fieldsArray.get(i).getAsJsonObject();
                    
                    String name = fieldJson.get("name").getAsString();
                    String label = fieldJson.get("label").getAsString();
                    String type = fieldJson.get("type").getAsString();
                    boolean nillable = fieldJson.get("nillable").getAsBoolean();
                    boolean custom = fieldJson.get("custom").getAsBoolean();
                    boolean calculated = fieldJson.has("calculated") && fieldJson.get("calculated").getAsBoolean();
                    
                    // Check if field is excluded from Bulk API (compound types, etc.)
                    boolean excluded = type.equals("address") || type.equals("location") || type.equals("base64");
                    
                    // Id field is always required
                    boolean required = name.equals("Id") || (!nillable && !calculated);
                    
                    FieldItem item = new FieldItem(name, label, type, custom, required, excluded);
                    
                    // Pre-select fields
                    if (preselectedFields == null) {
                        // No previous selection - select all queryable fields
                        item.setSelected(!excluded);
                    } else {
                        // Use previous selection, but always include required fields
                        item.setSelected(preselectedFields.contains(name) || required);
                    }
                    
                    fields.add(item);
                }
            }
        }
        
        // Sort: Id first, then required fields, then by name
        fields.sort((a, b) -> {
            if (a.getName().equals("Id")) return -1;
            if (b.getName().equals("Id")) return 1;
            if (a.isRequired() && !b.isRequired()) return -1;
            if (!a.isRequired() && b.isRequired()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        
        return fields;
    }
    
    /**
     * Model class representing a field in the selection dialog.
     */
    public static class FieldItem {
        private final SimpleStringProperty name;
        private final SimpleStringProperty label;
        private final SimpleStringProperty type;
        private final SimpleStringProperty info;
        private final SimpleBooleanProperty selected;
        private final boolean custom;
        private final boolean required;
        private final boolean excluded;
        
        public FieldItem(String name, String label, String type, boolean custom, boolean required, boolean excluded) {
            this.name = new SimpleStringProperty(name);
            this.label = new SimpleStringProperty(label);
            this.type = new SimpleStringProperty(type);
            this.custom = custom;
            this.required = required;
            this.excluded = excluded;
            this.selected = new SimpleBooleanProperty(false);
            
            // Build info string
            List<String> infoList = new ArrayList<>();
            if (required) infoList.add("Required");
            if (custom) infoList.add("Custom");
            if (excluded) infoList.add("Not Queryable");
            this.info = new SimpleStringProperty(String.join(", ", infoList));
        }
        
        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }
        
        public String getLabel() { return label.get(); }
        public SimpleStringProperty labelProperty() { return label; }
        
        public String getType() { return type.get(); }
        public SimpleStringProperty typeProperty() { return type; }
        
        public String getInfo() { return info.get(); }
        public SimpleStringProperty infoProperty() { return info; }
        
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { 
            // Cannot deselect required fields
            if (!value && required) return;
            // Cannot select excluded fields
            if (value && excluded) return;
            selected.set(value); 
        }
        public SimpleBooleanProperty selectedProperty() { return selected; }
        
        public boolean isCustom() { return custom; }
        public boolean isRequired() { return required; }
        public boolean isExcluded() { return excluded; }
    }
}

package ba.etfbl.luka.ui;

import ba.etfbl.luka.model.Institution;
import ba.etfbl.luka.model.Terminal;
import ba.etfbl.luka.model.Vessel;
import ba.etfbl.luka.model.VesselType;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class VesselDialog {
    private VesselDialog() {
    }

    public static Optional<Result> show(Config config) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle(config.title());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<Terminal> terminalCombo = new ComboBox<>(FXCollections.observableArrayList(config.terminals()));
        if (!config.terminals().isEmpty()) {
            terminalCombo.getSelectionModel().select(config.initialTerminal());
        }

        ComboBox<VesselType> typeCombo = new ComboBox<>(FXCollections.observableArrayList(VesselType.values()));
        typeCombo.getSelectionModel().select(config.initialType());

        Vessel existing = config.existing();
        TextField nameField = textField(existing == null ? "" : existing.getName());
        TextField imoField = textField(existing == null ? "" : existing.getImoNumber());
        TextField enginesField = textField(existing == null ? "1" : String.valueOf(existing.getEngineCount()));
        TextField registrationField = textField(existing == null ? "" : existing.getRegistrationNumber());
        TextField specificField = textField(existing == null ? "" : String.valueOf(existing.getSpecificValue()));
        TextField photoField = textField(existing == null ? "" : existing.getPhotoPath());
        TextField wantedFileField = textField(existing == null ? "" : existing.getWantedImoFilePath());
        photoField.setEditable(false);
        wantedFileField.setEditable(false);

        ComboBox<Institution> institutionCombo = new ComboBox<>(
                FXCollections.observableArrayList(Institution.supportedFor(config.initialType()))
        );
        institutionCombo.getSelectionModel().select(existing == null ? Institution.COMMERCIAL : existing.getInstitution());
        Label specificLabel = new Label(config.initialType().getExtraFieldLabel());
        CheckBox rotationBox = new CheckBox("Rotacija");
        rotationBox.setSelected(existing != null && existing.isRotationOn());
        rotationBox.disableProperty().bind(institutionCombo.valueProperty().isEqualTo(Institution.COMMERCIAL));

        typeCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            specificLabel.setText(newValue.getExtraFieldLabel());
            Institution selectedInstitution = institutionCombo.getValue();
            institutionCombo.setItems(FXCollections.observableArrayList(Institution.supportedFor(newValue)));
            if (selectedInstitution != null && Institution.supportedFor(newValue).contains(selectedInstitution)) {
                institutionCombo.getSelectionModel().select(selectedInstitution);
            } else {
                institutionCombo.getSelectionModel().select(Institution.COMMERCIAL);
            }
        });

        Button choosePhotoButton = new Button("Izaberi...");
        choosePhotoButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Fotografija plovila");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Slike", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                photoField.setText(file.getAbsolutePath());
            }
        });

        Button chooseWantedFileButton = new Button("Izaberi...");
        chooseWantedFileButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Fajl sa IMO potjernicama");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tekstualni fajlovi", "*.txt", "*.csv", "*.*"));
            File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                wantedFileField.setText(file.getAbsolutePath());
            }
        });
        wantedFileField.disableProperty().bind(institutionCombo.valueProperty().isNotEqualTo(Institution.COAST_GUARD));
        chooseWantedFileButton.disableProperty().bind(institutionCombo.valueProperty().isNotEqualTo(Institution.COAST_GUARD));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        int row = 0;
        if (!config.terminals().isEmpty()) {
            addRow(grid, row++, "Terminal", terminalCombo);
        }
        addRow(grid, row++, "Tip plovila", typeCombo);
        addRow(grid, row++, "Naziv", nameField);
        addRow(grid, row++, "IMO broj", imoField);
        addRow(grid, row++, "Broj motora", enginesField);
        addRow(grid, row++, "Registarski broj", registrationField);
        grid.add(specificLabel, 0, row);
        grid.add(specificField, 1, row++, 2, 1);
        addRow(grid, row++, "Institucija", institutionCombo);
        addRow(grid, row++, "", rotationBox);
        grid.add(new Label("Fotografija"), 0, row);
        grid.add(photoField, 1, row);
        grid.add(choosePhotoButton, 2, row++);
        grid.add(new Label("Potjernice"), 0, row);
        grid.add(wantedFileField, 1, row);
        grid.add(chooseWantedFileButton, 2, row);

        dialog.getDialogPane().setContent(grid);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                VesselType selectedType = typeCombo.getValue();
                Terminal selectedTerminal = terminalCombo.getValue();
                validate(config, selectedType, selectedTerminal, nameField, imoField, registrationField, enginesField, specificField);
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (!ButtonType.OK.equals(buttonType)) {
                return null;
            }
            VesselType selectedType = typeCombo.getValue();
            Vessel vessel = existing == null ? Vessel.create(selectedType) : existing;
            vessel.setType(selectedType);
            vessel.setName(nameField.getText().trim());
            vessel.setImoNumber(imoField.getText().trim());
            vessel.setEngineCount(parsePositiveInt(enginesField.getText(), "Broj motora"));
            vessel.setRegistrationNumber(registrationField.getText().trim());
            vessel.setSpecificValue(parsePositiveInt(specificField.getText(), selectedType.getExtraFieldLabel()));
            vessel.setPhotoPath(photoField.getText().trim());
            vessel.setInstitution(institutionCombo.getValue());
            vessel.setRotationOn(rotationBox.isSelected());
            vessel.setWantedImoFilePath(vessel.getInstitution() == Institution.COAST_GUARD ? wantedFileField.getText().trim() : "");
            return new Result(vessel, terminalCombo.getValue());
        });

        return dialog.showAndWait();
    }

    private static void validate(
            Config config,
            VesselType selectedType,
            Terminal selectedTerminal,
            TextField nameField,
            TextField imoField,
            TextField registrationField,
            TextField enginesField,
            TextField specificField
    ) {
        if (selectedType == null) {
            throw new IllegalArgumentException("Tip plovila je obavezno polje.");
        }
        if (!config.terminals().isEmpty() && selectedTerminal == null) {
            throw new IllegalArgumentException("Terminal je obavezno polje.");
        }
        validateText(nameField, "Naziv");
        validateText(imoField, "IMO broj");
        validateText(registrationField, "Registarski broj");
        if (config.duplicateImoChecker().test(imoField.getText().trim(), config.existing())) {
            throw new IllegalArgumentException("Plovilo sa tim IMO brojem vec postoji u luci.");
        }
        if (selectedTerminal != null && config.requiresFreeDockCheck().test(selectedTerminal, config.existing()) && !selectedTerminal.hasFreeDock()) {
            throw new IllegalArgumentException("Izabrani terminal nema slobodnih vezova.");
        }
        parsePositiveInt(enginesField.getText(), "Broj motora");
        parsePositiveInt(specificField.getText(), selectedType.getExtraFieldLabel());
    }

    private static TextField textField(String value) {
        TextField field = new TextField(value == null ? "" : value);
        field.setPrefColumnCount(24);
        return field;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node node) {
        grid.add(new Label(label), 0, row);
        grid.add(node, 1, row, 2, 1);
    }

    private static void validateText(TextField field, String label) {
        if (field.getText() == null || field.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(label + " je obavezno polje.");
        }
    }

    private static int parsePositiveInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " mora biti pozitivan cijeli broj.");
        }
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public record Config(
            String title,
            VesselType initialType,
            Vessel existing,
            List<Terminal> terminals,
            Terminal initialTerminal,
            BiPredicate<String, Vessel> duplicateImoChecker,
            BiPredicate<Terminal, Vessel> requiresFreeDockCheck
    ) {
    }

    public record Result(Vessel vessel, Terminal terminal) {
    }
}

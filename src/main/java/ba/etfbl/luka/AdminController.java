package ba.etfbl.luka;

import ba.etfbl.luka.model.Institution;
import ba.etfbl.luka.model.Port;
import ba.etfbl.luka.model.Terminal;
import ba.etfbl.luka.model.Vessel;
import ba.etfbl.luka.model.VesselType;
import ba.etfbl.luka.service.AppLogger;
import ba.etfbl.luka.service.PortConfig;
import ba.etfbl.luka.service.PortRepository;
import ba.etfbl.luka.ui.VesselDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class AdminController {
    private final PortRepository repository = new PortRepository();
    private final ObservableList<Vessel> visibleVessels = FXCollections.observableArrayList();

    private PortConfig config;
    private Port port;
    private boolean refreshingTerminalCombo;

    @FXML
    private ComboBox<Terminal> terminalCombo;
    @FXML
    private ComboBox<VesselType> vesselTypeCombo;
    @FXML
    private TableView<Vessel> vesselsTable;
    @FXML
    private TableColumn<Vessel, String> nameColumn;
    @FXML
    private TableColumn<Vessel, String> typeColumn;
    @FXML
    private TableColumn<Vessel, String> imoColumn;
    @FXML
    private TableColumn<Vessel, String> institutionColumn;
    @FXML
    private TableColumn<Vessel, String> registrationColumn;
    @FXML
    private TableColumn<Vessel, String> enteredColumn;
    @FXML
    private Label freeDocksLabel;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;

    @FXML
    private void initialize() {
        try {
            config = PortConfig.load();
            port = repository.load(config);

            terminalCombo.setItems(FXCollections.observableArrayList(port.getTerminals()));
            vesselTypeCombo.setItems(FXCollections.observableArrayList(VesselType.values()));
            vesselTypeCombo.getSelectionModel().selectFirst();

            configureTable();
            vesselsTable.setItems(visibleVessels);
            terminalCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (!refreshingTerminalCombo) {
                    refreshTable();
                }
            });
            vesselsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
                editButton.setDisable(selected == null);
                deleteButton.setDisable(selected == null);
            });

            if (!port.getTerminals().isEmpty()) {
                terminalCombo.getSelectionModel().selectFirst();
            }
            if (!config.warnings().isEmpty()) {
                showError(String.join(System.lineSeparator(), config.warnings()));
            }
        } catch (Exception exception) {
            AppLogger.error("Greska pri pokretanju administratorskog dijela.", exception);
            showError("Aplikacija nije uspjesno pokrenuta.");
        }
    }

    private void configureTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType().toString()));
        imoColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getImoNumber()));
        institutionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getInstitution().toString()));
        registrationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRegistrationNumber()));
        enteredColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getEnteredAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        ));
    }

    @FXML
    private void addVessel() {
        Terminal terminal = terminalCombo.getValue();
        VesselType type = vesselTypeCombo.getValue();
        if (terminal == null || type == null) {
            return;
        }
        if (!port.hasFreeDock()) {
            showError("Luka nema slobodnih vezova.");
            return;
        }

        showVesselDialog(type, null, terminal).ifPresent(result -> {
            try {
                result.terminal().addVessel(result.vessel());
                port.recordEntry(result.vessel());
                terminalCombo.setValue(result.terminal());
                repository.save(port);
                refreshTable();
            } catch (Exception exception) {
                AppLogger.error("Greska pri dodavanju plovila.", exception);
                showError(exception.getMessage());
            }
        });
    }

    @FXML
    private void editVessel() {
        Vessel selected = vesselsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Terminal currentTerminal = port.findTerminal(selected.getTerminalId()).orElse(terminalCombo.getValue());
        showVesselDialog(selected.getType(), selected, currentTerminal).ifPresent(result -> {
            try {
                if (!result.terminal().equals(currentTerminal)) {
                    currentTerminal.removeVessel(result.vessel());
                    result.terminal().addVessel(result.vessel());
                    terminalCombo.setValue(result.terminal());
                } else {
                    currentTerminal.normalizeDockIndexes();
                }
                repository.save(port);
                refreshTable();
            } catch (Exception exception) {
                AppLogger.error("Greska pri izmjeni plovila.", exception);
                showError(exception.getMessage());
            }
        });
    }

    @FXML
    private void deleteVessel() {
        Vessel selected = vesselsTable.getSelectionModel().getSelectedItem();
        Terminal terminal = terminalCombo.getValue();
        if (selected == null || terminal == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Obrisati izabrano plovilo?", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.showAndWait()
                .filter(ButtonType.YES::equals)
                .ifPresent(buttonType -> {
                    terminal.removeVessel(selected);
                    repository.save(port);
                    refreshTable();
                });
    }

    @FXML
    private void openClientApplication() {
        try {
            repository.save(port);
            FXMLLoader loader = new FXMLLoader(LukaApplication.class.getResource("client-view.fxml"));
            Scene scene = new Scene(loader.load(), 1000, 700);
            ClientController controller = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("Luka - Korisnicki dio");
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setScene(scene);
            stage.initModality(Modality.NONE);
            stage.setOnCloseRequest(event -> {
                controller.shutdown();
                reloadPort();
            });
            stage.show();
        } catch (IOException exception) {
            AppLogger.error("Greska pri otvaranju korisnickog dijela.", exception);
            showError("Korisnicki dio nije moguce otvoriti.");
        }
    }

    private Optional<VesselDialog.Result> showVesselDialog(VesselType type, Vessel existing, Terminal initialTerminal) {
        return VesselDialog.show(new VesselDialog.Config(
                existing == null ? "Dodavanje plovila" : "Izmjena plovila",
                type,
                existing,
                port.getTerminals(),
                initialTerminal,
                this::isDuplicateImo,
                (targetTerminal, vessel) -> vessel == null || targetTerminal != initialTerminal
        ));
    }

    private void refreshTable() {
        Terminal terminal = terminalCombo.getValue();
        visibleVessels.setAll(terminal == null ? List.of() : terminal.getVessels());
        freeDocksLabel.setText(terminal == null ? "" : "Slobodnih vezova: " + terminal.getFreeDockCount());
        refreshTerminalComboItems();
    }

    private void reloadPort() {
        int selectedTerminalId = terminalCombo.getValue() == null ? 1 : terminalCombo.getValue().getId();
        port = repository.load(config == null ? PortConfig.load() : config);
        terminalCombo.setItems(FXCollections.observableArrayList(port.getTerminals()));
        port.findTerminal(selectedTerminalId)
                .ifPresentOrElse(terminalCombo::setValue, () -> {
                    if (!port.getTerminals().isEmpty()) {
                        terminalCombo.getSelectionModel().selectFirst();
                    }
        });
        refreshTable();
    }

    private void refreshTerminalComboItems() {
        if (refreshingTerminalCombo || terminalCombo.getValue() == null) {
            return;
        }
        int selectedTerminalId = terminalCombo.getValue().getId();
        refreshingTerminalCombo = true;
        terminalCombo.setItems(FXCollections.observableArrayList(port.getTerminals()));
        port.findTerminal(selectedTerminalId).ifPresent(terminalCombo::setValue);
        refreshingTerminalCombo = false;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private boolean isDuplicateImo(String imoNumber, Vessel existing) {
        return port.getTerminals().stream()
                .flatMap(terminal -> terminal.getVessels().stream())
                .filter(vessel -> existing == null || !vessel.getId().equals(existing.getId()))
                .anyMatch(vessel -> vessel.getImoNumber() != null && vessel.getImoNumber().equalsIgnoreCase(imoNumber));
    }
}

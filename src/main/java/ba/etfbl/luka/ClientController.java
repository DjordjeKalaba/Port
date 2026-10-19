package ba.etfbl.luka;

import ba.etfbl.luka.model.Institution;
import ba.etfbl.luka.model.Port;
import ba.etfbl.luka.model.Terminal;
import ba.etfbl.luka.model.Vessel;
import ba.etfbl.luka.model.VesselType;
import ba.etfbl.luka.service.AppLogger;
import ba.etfbl.luka.service.CsvExporter;
import ba.etfbl.luka.service.InvestigationService;
import ba.etfbl.luka.service.PortConfig;
import ba.etfbl.luka.service.PortRepository;
import ba.etfbl.luka.simulation.EntryReservation;
import ba.etfbl.luka.simulation.GridPosition;
import ba.etfbl.luka.simulation.MovementDirection;
import ba.etfbl.luka.simulation.SimulationService;
import ba.etfbl.luka.simulation.TerminalLayout;
import ba.etfbl.luka.ui.VesselDialog;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClientController {
    private final PortRepository repository = new PortRepository();

    private Port port;
    private SimulationService simulation;
    private Timeline timeline;
    private boolean refreshingTerminalCombo;

    @FXML
    private Spinner<Integer> minimumVesselsSpinner;
    @FXML
    private ComboBox<Terminal> terminalCombo;
    @FXML
    private ComboBox<VesselType> addTypeCombo;
    @FXML
    private GridPane terminalGrid;
    @FXML
    private TextArea eventsArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Label vesselDetailsLabel;
    @FXML
    private ImageView vesselImageView;
    @FXML
    private Button startButton;
    @FXML
    private Button addVesselButton;

    @FXML
    private void initialize() {
        try {
            PortConfig config = PortConfig.load();
            port = repository.load(config);
            port.getTerminals().forEach(Terminal::normalizeDockIndexes);
            simulation = new SimulationService(port);
            repository.save(port);

            terminalCombo.setItems(FXCollections.observableArrayList(port.getTerminals()));
            addTypeCombo.setItems(FXCollections.observableArrayList(VesselType.values()));
            addTypeCombo.getSelectionModel().selectFirst();
            addVesselButton.setDisable(true);
            minimumVesselsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 200, 5));
            terminalCombo.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (!refreshingTerminalCombo) {
                    clearVesselDetails();
                    renderTerminal();
                }
            });

            if (!port.getTerminals().isEmpty()) {
                terminalCombo.getSelectionModel().selectFirst();
            }
            config.warnings().forEach(this::appendEvent);
            appendEvent("Korisnicki dio je spreman.");
        } catch (Exception exception) {
            AppLogger.error("Greska pri pokretanju korisnickog dijela.", exception);
            showError("Korisnicki dio nije uspjesno pokrenut.");
        }
    }

    @FXML
    private void startSimulation() {
        simulation.startThreadedSimulation(minimumVesselsSpinner.getValue());
        repository.save(port);

        startButton.setDisable(true);
        addVesselButton.setDisable(false);

        timeline = new Timeline(new KeyFrame(Duration.millis(250), event -> simulationStep()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        renderTerminal();
    }

    @FXML
    private void addVesselDuringSimulation() {
        VesselType type = addTypeCombo.getValue();
        if (type == null) {
            return;
        }

        if (simulation.findEntryReservation().isEmpty()) {
            showError("Luka je popunjena.");
            return;
        }

        boolean wasRunning = timeline != null && timeline.getStatus() == Animation.Status.RUNNING;
        if (wasRunning) {
            timeline.pause();
            simulation.setPaused(true);
        }

        try {
            showVesselDialog(type).ifPresent(result -> {
                Optional<EntryReservation> reservation = simulation.findEntryReservation();
                if (reservation.isEmpty()) {
                    showError("Luka je popunjena.");
                    return;
                }
                appendEvent(simulation.startEntering(reservation.get(), result.vessel()));
                repository.save(port);
                terminalCombo.setValue(reservation.get().dockReservation().terminal());
                renderTerminal();
            });
        } finally {
            if (wasRunning) {
                simulation.setPaused(false);
                timeline.play();
            }
        }
    }

    @FXML
    private void exportTaxes() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sacuvaj evidenciju naplate");
        chooser.setInitialFileName("lucke-takse.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(terminalGrid.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            CsvExporter.exportTaxRecords(port.getTaxRecords(), file.toPath());
            appendEvent("CSV evidencija sacuvana: " + file.getAbsolutePath());
        } catch (Exception exception) {
            AppLogger.error("CSV export nije uspio.", exception);
            showError(exception.getMessage());
        }
    }

    @FXML
    private void exportEntries() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sacuvaj evidenciju ulazaka");
        chooser.setInitialFileName("ulasci-u-luku.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(terminalGrid.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            CsvExporter.exportEntryRecords(port.getEntryRecords(), file.toPath());
            appendEvent("CSV evidencija ulazaka sacuvana: " + file.getAbsolutePath());
        } catch (Exception exception) {
            AppLogger.error("CSV export ulazaka nije uspio.", exception);
            showError(exception.getMessage());
        }
    }

    @FXML
    private void showInvestigations() {
        List<InvestigationService.SavedInvestigation> investigations = InvestigationService.listSaved();
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Evidencija uvidjaja");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea textArea = new TextArea(formatInvestigations(investigations));
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefColumnCount(90);
        textArea.setPrefRowCount(18);
        dialog.getDialogPane().setContent(textArea);
        dialog.showAndWait();
    }

    private void simulationStep() {
        simulation.tick();
        simulation.drainEvents().forEach(this::appendEvent);
        renderTerminal();

        if (simulation.isComplete()) {
            simulation.drainEvents().forEach(this::appendEvent);
            timeline.stop();
            simulation.shutdown();
            startButton.setDisable(false);
            addVesselButton.setDisable(true);
            repository.save(port);
            appendEvent("Simulacija je zavrsena. Stanje je sacuvano u luka.ser.");
        }
    }

    public void shutdown() {
        if (timeline != null) {
            timeline.stop();
        }
        if (simulation != null) {
            simulation.shutdown();
        }
        try {
            repository.save(port);
        } catch (Exception exception) {
            AppLogger.error("Greska pri zatvaranju korisnickog dijela.", exception);
        }
    }

    private Optional<VesselDialog.Result> showVesselDialog(VesselType initialType) {
        return VesselDialog.show(new VesselDialog.Config(
                "Novo plovilo",
                initialType,
                null,
                List.of(),
                null,
                (imoNumber, vessel) -> simulation.isDuplicateImo(imoNumber),
                (terminal, vessel) -> false
        ));
    }

    private void renderTerminal() {
        Terminal terminal = terminalCombo.getValue();
        terminalGrid.getChildren().clear();
        if (terminal == null) {
            return;
        }

        int channelLength = TerminalLayout.rowsFor(terminal);
        Map<GridPosition, Vessel> movingVessels = simulation.movingVessels(terminal);
        Map<GridPosition, MovementDirection> movingDirections = simulation.movingDirections(terminal);
        addCornerCell();
        for (int column = 0; column < channelLength; column++) {
            addHeaderCell(column + 1, "Polje " + (column + 1));
        }

        addRowLabel(1, "Gornji vezovi");
        addRowLabel(2, "Izlaz <");
        addRowLabel(3, "Ulaz >");
        addRowLabel(4, "Donji vezovi");

        for (int column = 0; column < channelLength; column++) {
            int topDockIndex = channelLength + (channelLength - 1 - column);
            Optional<Vessel> topDockVessel = dockVessel(terminal, topDockIndex);
            addDockCell(1, column + 1, topDockVessel, terminal, topDockIndex);

            GridPosition outboundPosition = new GridPosition(TerminalLayout.OUTBOUND_CHANNEL_ROW, column);
            Vessel outboundVessel = movingVessels.get(outboundPosition);
            addChannelCell(2, column + 1, outboundVessel, movingDirections.get(outboundPosition), false);

            GridPosition inboundPosition = new GridPosition(TerminalLayout.INBOUND_CHANNEL_ROW, column);
            Vessel inboundVessel = movingVessels.get(inboundPosition);
            addChannelCell(3, column + 1, inboundVessel, movingDirections.get(inboundPosition), true);

            Optional<Vessel> bottomDockVessel = dockVessel(terminal, column);
            addDockCell(4, column + 1, bottomDockVessel, terminal, column);
        }

        String blockedText = simulation.isBlocked(terminal) ? " | blokiran" : "";
        int freeDockCount = simulation.freeDockCount(terminal);
        String fullText = freeDockCount == 0 ? " | popunjen" : "";
        statusLabel.setText("Terminal " + terminal.getId() + " | slobodno "
                + freeDockCount + "/" + terminal.getDockCapacity()
                + " | luka slobodno " + simulation.totalFreeDocks()
                + blockedText + fullText);
        refreshTerminalComboItems();
    }

    private Optional<Vessel> dockVessel(Terminal terminal, int dockIndex) {
        return simulation.dockVessel(terminal, dockIndex);
    }

    private void addCornerCell() {
        Label label = new Label("Lijeva strana");
        label.setMinSize(104, 32);
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().addAll("terminal-cell", "terminal-header", "side-header");
        terminalGrid.add(label, 0, 0);
    }

    private void addHeaderCell(int column, String text) {
        Label label = new Label(text);
        label.setMinSize(82, 32);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        label.getStyleClass().addAll("terminal-cell", "terminal-header");
        terminalGrid.add(label, column, 0);
    }

    private void addRowLabel(int row, String text) {
        Label label = new Label(text);
        label.setMinSize(104, 52);
        label.setAlignment(Pos.CENTER);
        label.setWrapText(true);
        label.getStyleClass().addAll("terminal-cell", "row-header");
        terminalGrid.add(label, 0, row);
    }

    private void addDockCell(int row, int column, Optional<Vessel> vessel, Terminal terminal, int dockIndex) {
        String text = dockIndex >= terminal.getDockCapacity()
                ? ""
                : vessel.map(value -> "Vez " + (dockIndex + 1) + System.lineSeparator() + value.getDisplayCode())
                .orElse("Vez " + (dockIndex + 1) + System.lineSeparator() + "slobodno");
        Label label = createCell(text);
        label.getStyleClass().addAll("dock-cell", vessel.isPresent() ? "occupied-dock" : "empty-dock");
        vessel.ifPresent(value -> configureVesselCell(label, value, false));
        terminalGrid.add(label, column, row);
    }

    private void addChannelCell(int row, int column, Vessel vessel, MovementDirection direction, boolean inbound) {
        String emptyText = inbound
                ? ">" + System.lineSeparator() + "ulaz"
                : "<" + System.lineSeparator() + "izlaz";
        Label label = createCell(vessel == null ? emptyText : movementLabel(vessel, direction));
        label.getStyleClass().addAll("channel-cell", vessel == null ? "channel-empty" : "movement-cell");
        if (inbound) {
            label.getStyleClass().add("inbound-channel");
        } else {
            label.getStyleClass().add("outbound-channel");
        }
        if (vessel != null) {
            label.getStyleClass().add(movementStyle(direction));
            configureVesselCell(label, vessel, true);
        }
        terminalGrid.add(label, column, row);
    }

    private Label createCell(String text) {
        Label label = new Label(text);
        label.setMinSize(82, 52);
        label.setPrefSize(82, 52);
        label.setMaxSize(82, 52);
        label.setAlignment(Pos.CENTER);
        label.setWrapText(true);
        label.getStyleClass().add("terminal-cell");
        return label;
    }

    private String movementLabel(Vessel vessel, MovementDirection direction) {
        String prefix = "KRETANJE";
        if (direction == MovementDirection.ENTERING) {
            prefix = "ULAZ >";
        } else if (direction == MovementDirection.LEAVING) {
            prefix = "IZLAZ <";
        } else if (direction == MovementDirection.RESPONDING) {
            prefix = "SLUZBA";
        }
        return prefix + System.lineSeparator() + vessel.getDisplayCode();
    }

    private String movementStyle(MovementDirection direction) {
        if (direction == MovementDirection.ENTERING) {
            return "entering-cell";
        }
        if (direction == MovementDirection.LEAVING) {
            return "leaving-cell";
        }
        if (direction == MovementDirection.RESPONDING) {
            return "responding-cell";
        }
        return "movement-cell";
    }

    private void configureVesselCell(Label label, Vessel vessel, boolean moving) {
        label.getStyleClass().add(vesselTypeStyle(vessel));
        if (vessel.isGovernmentVessel()) {
            label.getStyleClass().add("government-vessel");
        }
        label.setTooltip(new Tooltip((moving ? "Plovilo u kretanju" : "Plovilo na vezu") + System.lineSeparator()
                + formatVesselDetails(vessel)));
        label.setOnMouseClicked(event -> showVesselDetails(vessel));
    }

    private String vesselTypeStyle(Vessel vessel) {
        return switch (vessel.getType()) {
            case CONTAINER_SHIP -> "container-vessel";
            case CRUISE_SHIP -> "cruise-vessel";
            case TANKER -> "tanker-vessel";
        };
    }

    private void showVesselDetails(Vessel vessel) {
        vesselDetailsLabel.setText(formatVesselDetails(vessel));
        updateVesselImage(vessel);
    }

    private void clearVesselDetails() {
        vesselDetailsLabel.setText("Klikni na plovilo za detalje.");
        vesselImageView.setImage(null);
    }

    private void updateVesselImage(Vessel vessel) {
        String photoPath = vessel.getPhotoPath();
        if (photoPath == null || photoPath.isBlank()) {
            vesselImageView.setImage(null);
            return;
        }
        try {
            Path path = Path.of(photoPath);
            if (!Files.isRegularFile(path)) {
                vesselImageView.setImage(null);
                return;
            }
            vesselImageView.setImage(new Image(path.toUri().toString(), 180, 130, true, true));
        } catch (Exception exception) {
            vesselImageView.setImage(null);
            AppLogger.error("Fotografija plovila nije ucitana.", exception);
        }
    }

    private String formatVesselDetails(Vessel vessel) {
        return "Plovilo: " + vessel.getName()
                + " | IMO: " + vessel.getImoNumber()
                + " | Tip: " + vessel.getType()
                + " | Institucija: " + vessel.getInstitution()
                + " | Rotacija: " + (vessel.isRotationOn() ? "da" : "ne")
                + " | Terminal: " + vessel.getTerminalId()
                + " | Vez: " + (vessel.getDockIndex() + 1);
    }

    private String formatInvestigations(List<InvestigationService.SavedInvestigation> investigations) {
        if (investigations.isEmpty()) {
            return "Nema sacuvanih uvidjaja.";
        }

        StringBuilder builder = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        for (InvestigationService.SavedInvestigation item : investigations) {
            builder.append("Tip: ").append(item.record().type()).append(System.lineSeparator())
                    .append("Terminal: ").append(item.record().terminalId()).append(System.lineSeparator())
                    .append("Vrijeme: ").append(item.record().happenedAt().format(formatter)).append(System.lineSeparator())
                    .append("IMO brojevi: ").append(String.join(", ", item.record().vesselImoNumbers())).append(System.lineSeparator())
                    .append("Fotografije: ").append(String.join(", ", item.record().photoPaths())).append(System.lineSeparator())
                    .append("Ucesnici: ").append(formatParticipants(item.record().participants())).append(System.lineSeparator())
                    .append("Fajl: ").append(item.path()).append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String formatParticipants(List<ba.etfbl.luka.model.InvestigationParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return "";
        }
        return participants.stream()
                .map(participant -> participant.name()
                        + " [" + participant.imoNumber()
                        + ", " + participant.vesselType()
                        + ", " + participant.institution()
                        + ", " + participant.registrationNumber() + "]")
                .toList()
                .toString();
    }

    private void appendEvent(String message) {
        eventsArea.appendText(java.time.LocalDateTime.now() + " - " + message + System.lineSeparator());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
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
}

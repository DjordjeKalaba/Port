module ba.etfbl.luka {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;

    exports ba.etfbl;
    exports ba.etfbl.luka;
    exports ba.etfbl.luka.model;

    opens ba.etfbl.luka to javafx.fxml;
}

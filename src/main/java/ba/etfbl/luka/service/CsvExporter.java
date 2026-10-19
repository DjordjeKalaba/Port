package ba.etfbl.luka.service;

import ba.etfbl.luka.model.PortTaxRecord;
import ba.etfbl.luka.model.PortEntryRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvExporter {
    private CsvExporter() {
    }

    public static void exportTaxRecords(List<PortTaxRecord> records, Path target) {
        StringBuilder csv = new StringBuilder("IMO,Naziv,Ulazak,Izlazak,Iznos KM%n".formatted());
        for (PortTaxRecord record : records) {
            csv.append(escape(record.imoNumber())).append(',')
                    .append(escape(record.vesselName())).append(',')
                    .append(record.enteredAt()).append(',')
                    .append(record.exitedAt()).append(',')
                    .append(record.chargedKm()).append(System.lineSeparator());
        }

        try {
            Files.writeString(target, csv.toString());
        } catch (IOException exception) {
            AppLogger.error("Neuspjesan export CSV evidencije.", exception);
            throw new IllegalStateException("CSV fajl nije sacuvan.", exception);
        }
    }

    public static void exportEntryRecords(List<PortEntryRecord> records, Path target) {
        StringBuilder csv = new StringBuilder("IMO,Naziv,Terminal,Ulazak%n".formatted());
        for (PortEntryRecord record : records) {
            csv.append(escape(record.imoNumber())).append(',')
                    .append(escape(record.vesselName())).append(',')
                    .append(record.terminalId()).append(',')
                    .append(record.enteredAt()).append(System.lineSeparator());
        }

        try {
            Files.writeString(target, csv.toString());
        } catch (IOException exception) {
            AppLogger.error("Neuspjesan export CSV evidencije ulazaka.", exception);
            throw new IllegalStateException("CSV fajl nije sacuvan.", exception);
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") ? "\"" + escaped + "\"" : escaped;
    }
}

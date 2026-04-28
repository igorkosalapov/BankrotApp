package com.bankrotapp.controller;

import com.bankrotapp.model.Creditor;
import com.bankrotapp.model.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewInputParserTest {

    private final PreviewInputParser parser = new PreviewInputParser();

    @Test
    void testCreditorParsingFromNewLineFormat() {
        List<Creditor> creditors = parser.parseCreditors("АО Альфа-Банк|Москва|loan|AB-1|01.01.2024|150000|Справка|3");
        assertEquals(1, creditors.size());
        assertEquals("АО Альфа-Банк", creditors.get(0).name());
        assertEquals(1, creditors.get(0).contracts().size());
        assertTrue(creditors.get(0).contracts().get(0).contractNumber().contains("AB-1"));
    }

    @Test
    void testGroupMultipleContractsByCreditor() {
        List<Creditor> creditors = parser.parseCreditors("АО Альфа-Банк|Москва|loan|AB-1|01.01.2024|100|Справка|1\nАО Альфа-Банк|Москва|loan|AB-2|02.01.2024|200|Справка|1");
        assertEquals(1, creditors.size());
        assertEquals(2, creditors.get(0).contracts().size());
    }

    @Test
    void testVehicleParsing() {
        List<Vehicle> vehicles = parser.parseVehicles("легковой|Lada|Vesta|А111АА77|2020|VIN1|ENG1|Собственность|гараж|500000|нет");
        assertEquals(1, vehicles.size());
        assertEquals("Lada", vehicles.get(0).brand());
        assertEquals(2020, vehicles.get(0).year());
    }

    @Test
    void testEmptyVehicleTextareaMeansNoVehicles() {
        List<Vehicle> vehicles = parser.parseVehicles("");
        assertTrue(vehicles.isEmpty());
    }

    @Test
    void testOldCreditorFormatStillParses() {
        List<Creditor> creditors = parser.parseCreditors("Банк|Договор|1000");
        assertEquals(1, creditors.size());
        assertEquals(1, creditors.get(0).contracts().size());
    }
}

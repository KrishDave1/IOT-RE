package com.iot;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * IoT Thermostat implementation using WoT-TD, Drools, and Apache Jena.
 */
public class IoTThermostat {
    // Temperature state
    private static double currentTemperature = 22.0;  // Default temp in Celsius
    private static boolean heaterOn = false;

    public static void main(String[] args) {
        System.out.println("IoT Thermostat System Starting...");

        // Step 1: Define WoT Thing Description
        defineThingDescription();

        // Step 2: Apply Drools Rules for Thermostat Control
        applyDroolsRules();

        // Step 3: Store Data in Apache Jena Triple Store
        storeInTripleStore();

        System.out.println("System Execution Completed.");
    }

    /**
     * Step 1: Defines the Thing Description (TD) for the thermostat.
     */
    public static void defineThingDescription() {
        System.out.println("Defining Thing Description...");
        String thingDescription = "{\n" +
            "    \"@context\": \"https://www.w3.org/2019/wot/td/v1\",\n" +
            "    \"id\": \"urn:dev:ops:thermostat-1234\",\n" +
            "    \"title\": \"IoT Thermostat\",\n" +
            "    \"description\": \"A smart thermostat that controls room temperature.\",\n" +
            "    \"properties\": {\n" +
            "        \"temperature\": {\n" +
            "            \"type\": \"number\",\n" +
            "            \"description\": \"Current temperature\",\n" +
            "            \"readOnly\": true\n" +
            "        }\n" +
            "    },\n" +
            "    \"actions\": {\n" +
            "        \"setTemperature\": {\n" +
            "            \"input\": { \"type\": \"number\" },\n" +
            "            \"output\": { \"type\": \"string\" }\n" +
            "        }\n" +
            "    },\n" +
            "    \"events\": {\n" +
            "        \"overheat\": {\n" +
            "            \"data\": { \"type\": \"string\" },\n" +
            "            \"description\": \"Triggered when temperature exceeds limit.\"\n" +
            "        }\n" +
            "    }\n" +
            "}";
        
        System.out.println("Thing Description Defined:\n" + thingDescription);
    }

    /**
     * Step 2: Applies Drools rules for temperature control.
     */
    public static void applyDroolsRules() {
        System.out.println("Applying Drools Rules...");

        try {
            KieServices ks = KieServices.Factory.get();
            KieContainer kContainer = ks.getKieClasspathContainer();
            KieSession kSession = kContainer.newKieSession("rulesSession");

            if (kSession == null) {
                System.err.println("Error: KieSession 'rulesSession' is not available.");
                return;
            }
    
            // Example Temperature Event
            Temperature temp = new Temperature(35);  // Simulating a high temp event
            kSession.insert(temp);
            kSession.fireAllRules();
            kSession.dispose();

            System.out.println("Rules Applied Successfully.");
        } catch (Exception e) {
            System.err.println("Error in Drools Execution: " + e.getMessage());
        }
    }

    /**
     * Step 3: Stores the thermostat state in an Apache Jena Triple Store.
     */
    public static void storeInTripleStore() {
        System.out.println("Storing Data in Triple Store...");

        Model model = ModelFactory.createDefaultModel();
        String ns = "http://example.org/iot/";

        Resource thermostat = model.createResource(ns + "Thermostat1234")
                .addProperty(RDF.type, "IoTDevice")
                .addProperty(model.createProperty(ns + "hasTemperature"),
                        model.createTypedLiteral(currentTemperature))
                .addProperty(model.createProperty(ns + "heaterStatus"),
                        model.createTypedLiteral(heaterOn));

        // Save to file
        try (OutputStream out = new FileOutputStream("thermostat_data.ttl")) {
            RDFDataMgr.write(out, model, RDFFormat.TURTLE);
            System.out.println("Data Stored Successfully in Triple Store.");
        } catch (Exception e) {
            System.err.println("Error Storing Data: " + e.getMessage());
        }
    }
}

/**
 * Class representing the Thermostat state for the Drools Rule Engine.
 */
class ThermostatState {
    private double temperature;
    private boolean heaterOn;

    public ThermostatState(double temperature, boolean heaterOn) {
        this.temperature = temperature;
        this.heaterOn = heaterOn;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isHeaterOn() {
        return heaterOn;
    }

    public void setHeaterOn(boolean heaterOn) {
        this.heaterOn = heaterOn;
    }
}

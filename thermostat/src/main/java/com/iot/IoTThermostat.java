package com.iot;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.eclipse.paho.client.mqttv3.*;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

/**
 * Enhanced IoT Thermostat with MQTT, WoT-TD, Drools, and Apache Jena
 */
public class IoTThermostat {
    // System state
    private static double currentTemperature = 22.0;
    private static boolean heaterOn = false;
    
    // MQTT Configuration
    private static final String MQTT_BROKER = "ssl://a45c919ea02947259f6b40286afaadf4.s1.eu.hivemq.cloud:8883";
    private static final String MQTT_USERNAME = "Krish";
    private static final String MQTT_PASSWORD = "Krishrakesh@1";
    private static final String TEMP_TOPIC = "thermostat/temperature";
    private static final String STATUS_TOPIC = "thermostat/status";
    private static final String HEATER_TOPIC = "thermostat/heaterStatus";
    private static final String ALERT_TOPIC = "thermostat/alerts";
    
    // System components
    private static MqttClient mqttClient;
    private static KieContainer kieContainer;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final ExecutorService ruleExecutor = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        System.out.println("=== IoT Thermostat System Starting ===");
        
        // Initialize components
        initializeDrools();
        defineThingDescription();
        setupMqtt();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownSystem();
        }));
        
        System.out.println("=== System Operational ===");
    }

    // ================== Initialization Methods ================== //

    public static void initializeDrools() {
        try {
            System.out.println("Initializing Drools Rule Engine...");
            KieServices kieServices = KieServices.Factory.get();
            kieContainer = kieServices.newKieClasspathContainer();

            // Pre-load the KieBase to avoid repeated loading
            kieContainer.getKieBase("thermostatRules");
            System.out.println("Drools initialized successfully. Available KieBases: " + 
            kieContainer.getKieBaseNames());
        } catch (Exception e) {
            System.err.println("Drools initialization failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void defineThingDescription() {
        System.out.println("\n=== Thing Description ===");
        String td = """
        {
            "@context": "https://www.w3.org/2019/wot/td/v1",
            "id": "urn:dev:ops:thermostat-1234",
            "title": "Smart Thermostat",
            "description": "IoT thermostat with rule-based temperature control",
            "properties": {
                "temperature": {
                    "type": "number",
                    "unit": "°C",
                    "readOnly": true
                },
                "heaterStatus": {
                    "type": "boolean",
                    "description": "Heater on/off state"
                }
            },
            "actions": {
                "setTargetTemperature": {
                    "input": { "type": "number" },
                    "output": { "type": "string" }
                }
            },
            "events": {
                "temperatureAlert": {
                    "data": { "type": "string" },
                    "description": "Triggered when temperature exceeds thresholds"
                }
            }
        }""";
        System.out.println(td);
    }

    // ================== MQTT Methods ================== //

    public static void setupMqtt() {
        try {
            // Add SSL context configuration
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            sslContext.init(null, tmf.getTrustManagers(), null);
            
            System.out.println("\nConnecting to MQTT Broker...");
            
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);
            options.setUserName(MQTT_USERNAME);
            options.setPassword(MQTT_PASSWORD.toCharArray());
            options.setMaxReconnectDelay(30000); // 30 seconds max delay
    
            mqttClient = new MqttClient(MQTT_BROKER, MqttClient.generateClientId());
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println((reconnect ? "Reconnected" : "Connected") + 
                    " to MQTT broker: " + serverURI + 
                    " (ServerURI: " + mqttClient.getServerURI() + ")");
                    try {
                        // Resubscribe on reconnect
                        mqttClient.subscribe(TEMP_TOPIC, 1, (topic, msg) -> {
                            handleMqttMessage(topic, msg);
                        });
                        System.out.println("Resubscribed to topics");
                    } catch (MqttException e) {
                        System.err.println("Resubscription failed: " + e.getMessage());
                    }
                }
            
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("Connection lost: " + cause.getMessage());
                }
    
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMqttMessage(topic, message);
                }
    
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Optional: Add message delivery confirmation handling
                }
            });
    
            mqttClient.connect(options);
            
            // Schedule periodic status updates
            scheduler.scheduleAtFixedRate(() -> {
                System.out.println("Network stats - " + 
                    "Pending deliveries: " + mqttClient.getPendingDeliveryTokens().length + 
                    ", Connected: " + mqttClient.isConnected());
            }, 1, 1, TimeUnit.MINUTES);
            
        } catch (MqttException e) {
            System.err.println("MQTT Connection Error:");
            e.printStackTrace();
            System.exit(1);
        } catch (java.security.NoSuchAlgorithmException e) {
            System.err.println("SSL Algorithm not available:");
            e.printStackTrace();
            System.exit(1);
        } catch (java.security.KeyStoreException e) {
            System.err.println("KeyStore Error:");
            e.printStackTrace();
            System.exit(1);
        } catch (java.security.KeyManagementException e) {
            System.err.println("Key Management Error:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void subscribeToTopics() {
        try {
            mqttClient.subscribe(TEMP_TOPIC, 1);
            System.out.println("Subscribed to topic: " + TEMP_TOPIC);
        } catch (MqttException e) {
            System.err.println("Subscription failed:");
            e.printStackTrace();
        }
    }

    private static void handleMqttMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            System.out.printf("\nReceived message on %s: %s%n", topic, payload);
            
            if (TEMP_TOPIC.equals(topic)) {
                JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
                double newTemp = json.get("temperature").getAsDouble();
                String timestamp = json.get("timestamp").getAsString();
                
                // Process in separate thread
                CompletableFuture.runAsync(() -> {
                    updateTemperature(newTemp, timestamp);
                });
            }
        } catch (Exception e) {
            System.err.println("Error processing MQTT message:");
            e.printStackTrace();
            
            try {
                String errorMsg = "{\"error\":\"Invalid temperature data\",\"message\":\"" + 
                    e.getMessage() + "\"}";
                mqttClient.publish(ALERT_TOPIC, errorMsg.getBytes(), 1, false);
            } catch (MqttException mqttEx) {
                System.err.println("Failed to publish error message:");
                mqttEx.printStackTrace();
            }
        }
    }

    // ================== Business Logic Methods ================== //

    public static void applyDroolsRules(Temperature temp, ThermostatState state) {
        KieSession kSession = null;
        try {
            System.out.println("Creating new KieSession...");
            kSession = kieContainer.newKieSession("thermostatSession");
            
            System.out.println("Inserting facts - Temp: " + temp.getValue() + 
                             ", HeaterState: " + state.isHeaterOn());
            kSession.insert(temp);
            kSession.insert(state);
    
            int firedRules = kSession.fireAllRules();
            System.out.println("Fired " + firedRules + " rules");
            
            // Update heater state based on rules
            heaterOn = state.isHeaterOn();
            if (firedRules > 0) {
                System.out.println("Heater state changed to: " + (heaterOn ? "ON" : "OFF"));
                publishHeaterStatus();
            }
        } catch (Exception e) {
            System.err.println("Drools rule execution failed:");
            e.printStackTrace();
        } finally {
            if (kSession != null) {
                kSession.dispose();
            }
        }
    }

    public static void checkTemperatureAlerts() {
        if (currentTemperature > 25) {
            sendAlert("WARNING: High temperature detected (" + currentTemperature + "°C)");
        } else if (currentTemperature < 18) {
            sendAlert("WARNING: Low temperature detected (" + currentTemperature + "°C)");
        }
    }

    // ================== Data Persistence Methods ================== //

    public static void storeInTripleStore() {
        try {
            Model model = ModelFactory.createDefaultModel();
            String ns = "http://iot.org/thermostat/";
            
            Resource reading = model.createResource(ns + "Reading/" + System.currentTimeMillis())
                .addProperty(RDF.type, model.createResource(ns + "TemperatureReading"))
                .addProperty(model.createProperty(ns + "value"),
                    model.createTypedLiteral(currentTemperature))
                .addProperty(model.createProperty(ns + "unit"),
                    model.createLiteral("°C"))
                .addProperty(model.createProperty(ns + "timestamp"),
                    model.createTypedLiteral(System.currentTimeMillis()))
                .addProperty(model.createProperty(ns + "heaterState"),
                    model.createTypedLiteral(heaterOn));

            try (OutputStream out = new FileOutputStream("thermostat_data.ttl")) {
                RDFDataMgr.write(out, model, RDFFormat.TURTLE);
                System.out.println("Data persisted to RDF store");
            }
        } catch (Exception e) {
            System.err.println("Error persisting data:");
            e.printStackTrace();
        }
    }

    // ================== MQTT Publishing Methods ================== //

    public static void publishSystemStatus() {
        try {
            String status = String.format(
                "{\"temperature\":%.2f,\"heaterOn\":%b,\"timestamp\":%d}",
                currentTemperature, heaterOn, System.currentTimeMillis());
            
            mqttClient.publish(STATUS_TOPIC, status.getBytes(), 1, false);
            System.out.println("Published system status");
        } catch (MqttException e) {
            System.err.println("Failed to publish status:");
            e.printStackTrace();
        }
    }

    private static synchronized void updateTemperature(double newTemp, String timestamp) {
        if (newTemp != currentTemperature) {
            currentTemperature = newTemp;
            System.out.printf("Temperature updated to: %.2f°C at %s%n", 
                currentTemperature, timestamp);
            
            // Create facts for Drools
            Temperature temp = new Temperature(currentTemperature);
            ThermostatState state = new ThermostatState(heaterOn);
            
            applyDroolsRules(temp, state);
            storeInTripleStore();
            checkTemperatureAlerts();
        }
    }

    public static void publishHeaterStatus() {
        try {
            String status = heaterOn ? "ON" : "OFF";
            mqttClient.publish(HEATER_TOPIC, status.getBytes(), 1, false);
            System.out.println("Published heater status: " + status);
        } catch (MqttException e) {
            System.err.println("Failed to publish heater status:");
            e.printStackTrace();
        }
    }

    public static void sendAlert(String message) {
        try {
            mqttClient.publish(ALERT_TOPIC, message.getBytes(), 1, false);
            System.out.println("Published alert: " + message);
        } catch (MqttException e) {
            System.err.println("Failed to publish alert:");
            e.printStackTrace();
        }
    }

    // ================== System Management Methods ================== //

    public static void shutdownSystem() {
        System.out.println("\n=== Shutting down system ===");
        try {
            // Disconnect MQTT
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                System.out.println("Disconnected from MQTT broker");
            }
            
            // Shutdown scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            System.out.println("Scheduler shutdown complete");
        } catch (Exception e) {
            System.err.println("Error during shutdown:");
            e.printStackTrace();
        }
        System.out.println("=== System shutdown complete ===");
    }
}
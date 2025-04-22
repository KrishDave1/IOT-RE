import paho.mqtt.client as mqtt
import random
import time
import json
import logging
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# MQTT Settings
BROKER = "a45c919ea02947259f6b40286afaadf4.s1.eu.hivemq.cloud"
PORT = 8883
USERNAME = "Krish"
PASSWORD = "Krishrakesh@1"
TEMP_TOPIC = "thermostat/temperature"
HEATER_TOPIC = "thermostat/heaterStatus"
CLIENT_ID = f"thermostat-simulator-{random.randint(1000, 9999)}"

# Simulation settings
MIN_TEMP = 18.0
MAX_TEMP = 25.0
INTERVAL_SEC = 10
BASE_DRIFT = 0.3  # Base temperature change per interval
DRIFT_VARIANCE = 0.2  # Random variation in drift

class TemperatureSimulator:
    def __init__(self):
        self.current_temp = 22.0
        self.heater_on = False
        self.last_direction = 1  # 1 for increasing, -1 for decreasing
        
    def update_heater_status(self, status):
        self.heater_on = (status == "ON")
        logger.info(f"Heater status updated: {'ON' if self.heater_on else 'OFF'}")
        
    def calculate_new_temp(self):
        # Base change based on heater status
        base_change = BASE_DRIFT * (1 if self.heater_on else -1)
        
        # Add random variation
        variation = random.uniform(-DRIFT_VARIANCE, DRIFT_VARIANCE)
        
        # Add momentum factor (25% chance to continue previous direction)
        if random.random() < 0.25:
            base_change += self.last_direction * 0.2
            
        # Calculate new temp
        new_temp = self.current_temp + base_change + variation
        
        # Keep within bounds
        new_temp = max(MIN_TEMP, min(MAX_TEMP, new_temp))
        
        # Update state
        self.last_direction = 1 if (new_temp > self.current_temp) else -1
        self.current_temp = new_temp
        
        return round(self.current_temp, 2)

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logger.info("Connected to HiveMQ Cloud")
        client.subscribe(TEMP_TOPIC)
        client.subscribe(HEATER_TOPIC)
    else:
        logger.error(f"Connection failed with code {rc}")

def on_disconnect(client, userdata, rc):
    if rc != 0:
        logger.warning(f"Unexpected disconnection (rc={rc}), attempting reconnect")

def on_message(client, userdata, msg):
    try:
        if msg.topic == HEATER_TOPIC:
            simulator.update_heater_status(msg.payload.decode())
    except Exception as e:
        logger.error(f"Error processing message: {str(e)}")

def create_mqtt_client(simulator):
    client = mqtt.Client(
        client_id=CLIENT_ID,
        clean_session=True,
        protocol=mqtt.MQTTv311
    )
    client.username_pw_set(USERNAME, PASSWORD)
    client.tls_set()
    
    client.on_connect = on_connect
    client.on_message = on_message
    client.on_disconnect = on_disconnect
    
    client.reconnect_delay_set(min_delay=1, max_delay=120)
    return client

def run_simulation():
    global simulator
    simulator = TemperatureSimulator()
    
    client = create_mqtt_client(simulator)
    
    try:
        client.connect(BROKER, PORT, keepalive=60)
        client.loop_start()
        
        logger.info("Starting temperature simulation...")
        while True:
            timestamp = datetime.utcnow().isoformat()
            temperature = simulator.calculate_new_temp()
            
            payload = json.dumps({
                "timestamp": timestamp,
                "temperature": temperature,
                "unit": "Celsius"
            })
            
            result = client.publish(
                TEMP_TOPIC,
                payload=payload,
                qos=1,
                retain=False
            )
            
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                logger.info(f"Published: {temperature}°C (Heater: {'ON' if simulator.heater_on else 'OFF'})")
            else:
                logger.error(f"Publish failed: {result.rc}")
            
            time.sleep(INTERVAL_SEC)
            
    except KeyboardInterrupt:
        logger.info("Simulation stopped by user")
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}")
    finally:
        client.loop_stop()
        client.disconnect()
        logger.info("Disconnected from MQTT broker")

if __name__ == "__main__":
    run_simulation()
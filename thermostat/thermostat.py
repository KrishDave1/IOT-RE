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
TOPIC = "thermostat/temperature"
CLIENT_ID = f"thermostat-simulator-{random.randint(1000, 9999)}"

# Simulation settings
MIN_TEMP = 18.0
MAX_TEMP = 25.0
INTERVAL_SEC = 10  # Reduced from 5 to 10 seconds to be gentler on the broker

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logger.info("Connected to HiveMQ Cloud")
        client.subscribe(TOPIC)
    else:
        logger.error(f"Connection failed with code {rc}")

def on_disconnect(client, userdata, rc):
    if rc != 0:
        logger.warning(f"Unexpected disconnection (rc={rc}), attempting reconnect")

def get_simulated_temperature():
    """Generate realistic temperature fluctuations"""
    base_temp = random.uniform(MIN_TEMP, MAX_TEMP)
    # Add small fluctuations
    fluctuation = random.uniform(-0.5, 0.5)
    return round(base_temp + fluctuation, 2)

def create_mqtt_client():
    client = mqtt.Client(
        client_id=CLIENT_ID,
        clean_session=True,
        protocol=mqtt.MQTTv311
    )
    client.username_pw_set(USERNAME, PASSWORD)
    client.tls_set()  # Enable TLS
    
    # Configure callback methods
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    
    # Enable automatic reconnect
    client.reconnect_delay_set(min_delay=1, max_delay=120)
    return client

def run_simulation():
    client = create_mqtt_client()
    
    try:
        client.connect(BROKER, PORT, keepalive=60)
        client.loop_start()
        
        logger.info("Starting temperature simulation...")
        while True:
            timestamp = datetime.utcnow().isoformat()
            temperature = get_simulated_temperature()
            
            # Create structured payload
            payload = json.dumps({
                "timestamp": timestamp,
                "temperature": temperature,
                "unit": "Celsius"
            })
            
            # Publish with QoS 1 for at-least-once delivery
            result = client.publish(
                TOPIC,
                payload=payload,
                qos=1,
                retain=False
            )
            
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                logger.info(f"Published: {temperature}°C (mid: {result.mid})")
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
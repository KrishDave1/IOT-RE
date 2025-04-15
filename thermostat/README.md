# IOT-RE

# Smart Thermostat System with MQTT and Drools

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
![Architecture](https://img.shields.io/badge/Architecture-Client%2FServer-brightgreen)

An IoT system that simulates thermostat operation using MQTT for communication, Drools for rule-based decision making, and RDF for data persistence.

## Features

- **Real-time Temperature Monitoring**
- **Rule-based Heater Control** using Drools
- **MQTT Cloud Integration** (HiveMQ)
- **Semantic Data Storage** in RDF/Turtle format
- **Alert System** for temperature thresholds
- **W3C WoT-compliant** Thing Description

## Architecture

```mermaid
graph TD
    A[Python Simulator] -->|Publishes| B[MQTT Cloud]
    B -->|Subscribes| C[Java Controller]
    C --> D{Drools Engine}
    D -->|Rules Applied| E[Heater Status]
    C --> F[RDF Triple Store]
    E -->|Updated Status| B
```

Installation

1. Clone the Repository

git clone https://github.com/KrishDave1/IOT-RE.git
cd IOT-RE\thermostat

pip install paho-mqtt

Running the System
Start Python Simulator

cd thermostat
python thermostat.py

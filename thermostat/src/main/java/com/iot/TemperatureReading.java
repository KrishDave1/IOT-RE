package com.iot;

public class TemperatureReading {
    private final double value;
    
    public TemperatureReading(double value) {
        this.value = value;
    }
    
    public double getValue() {
        return value;
    }
}
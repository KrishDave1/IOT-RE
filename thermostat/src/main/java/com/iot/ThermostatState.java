package com.iot;

public class ThermostatState {
    private boolean heaterOn;
    
    public ThermostatState(boolean initialState) {
        this.heaterOn = initialState;
    }
    
    public boolean isHeaterOn() {
        return heaterOn;
    }
    
    public void setHeaterOn(boolean heaterOn) {
        this.heaterOn = heaterOn;
    }
}
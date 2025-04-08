package com.iot;

public class HeaterState {
    private boolean on;
    
    public HeaterState(boolean initialState) {
        this.on = initialState;
    }
    
    public boolean isOn() {
        return on;
    }
    
    public void setOn(boolean newState) {
        this.on = newState;
    }
}
package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Env {
    public Constants c;	// car profile
    public Fueling f;	// car profile
    public PID pid;
    public SAE sae;
    public Env (Preferences prefs) {
	this.f = new Fueling(prefs);
	this.c = new Constants(prefs);
	this.pid = new PID(prefs);
	this.sae = new SAE(prefs);
    }
}

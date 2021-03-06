package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Preset {
    protected Preferences prefs;

    public Preferences getPreferences() {
	System.out.println("preset get prefs wrong\n");
	return Preferences.userNodeForPackage(Preset.class).node("presets");
    }

    public Preset(Comparable<?> name) {
	this.prefs=getPreferences().node(name.toString());
    }

    public Preset(String name) {
	this.prefs=getPreferences().node(name);
    }

    // GETS
    protected Comparable<?>[] getArray(Comparable<?> what) {
	int num = this.prefs.getInt("num_" + what, 0);
	// System.out.println(what + ":" + num);
	String[] out = new String[num];
	Preferences p = this.prefs.node(what.toString());
	for(int i=0; i<num; i++) {
	    out[i] = p.get("" + i, "");
	    // System.out.println(what + ":" + i + ":" + out[i]);
	}
	return out;
    }

    // PUTS
    protected void putArray(Comparable<?> what, Comparable<?>[] in) {
	int i=0;
	this.prefs.putInt("num_" + what, in.length);
	Preferences p = this.prefs.node(what.toString());
	for(Comparable<?> s : in)
	    p.put("" + (i++), s.toString());
    }

    // misc
    protected String name() { return this.prefs.name(); }
    public String toString() { return this.prefs.name(); }
}

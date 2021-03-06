package org.nyet.logfile;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import au.com.bytecode.opencsv.*;

import org.nyet.util.DoubleArray;

public class Dataset {
    private String [] ids;
    private String [] units;
    private String fileId;
    private ArrayList<Column> columns;
    private ArrayList<Range> range_cache = new ArrayList<Range>();
    private int rows;
    protected ArrayList<String> lastFilterReasons = new ArrayList<String>();

    public class Range {
	public int start;
	public int end;
	public Range(int s, int e) { this.start=s; this.end=e; }
	public Range(int s) { this(s,s); }
	public Range() { this(0,0); }
	public int size() { return this.end-this.start+1; }
	public String toString() {
	    return String.format("[%d:%d]", start, end);
	}
    }

    public class Column {
	private Comparable<?> id;
	private String units;
	public DoubleArray data;

	public Column(Comparable<?> id, String units) {
	    this(id, units, new DoubleArray());
	}
	public Column(Comparable<?> id, String units, DoubleArray data) {
	    this.id = id;
	    this.units = units;
	    this.data = data;
	}

	public void add(String s) {
	    // nuke non-printable chars
	    final Pattern p = Pattern.compile("[^\\p{Print}]");
	    s=p.matcher(s).replaceAll("");

	    // look for time stamps, convert to seconds
	    final Pattern p1 = Pattern.compile("\\d{2}:\\d{2}:\\d{2}.\\d{3}");
	    final Pattern p2 = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");

	    SimpleDateFormat fmt=null;
	    if (p1.matcher(s).matches()) {
		fmt = new SimpleDateFormat("HH:mm:ss.SSS");
	    } else if (p2.matcher(s).matches()) {
		fmt = new SimpleDateFormat("HH:mm:ss");
	    }
	    if (fmt != null) {
		try {
		    Date d = fmt.parse(s);
		    data.append(Double.valueOf(d.getTime())/1000);
		} catch (Exception e) {
		}
	    } else {
		try {
		    data.append(Double.valueOf(s));
		} catch (Exception e) {
		}
	    }
	}

	public String getId() {
	    if(this.id==null) return null;
	    return this.id.toString();
	}
	public String getUnits() { return this.units; }
    }

    public class Key implements Comparable<Object> {
	private String fn;
	private String s;
	private Integer range;
	private BitSet flags;

	public Key (String fn, String s, int range, BitSet flags) {
	    this.fn=fn;
	    this.s=s;
	    this.range=new Integer(range);
	    this.flags=flags;
	}

	public Key (Key k) {
	    this.fn=k.fn;
	    this.s= new String(k.s);
	    this.range=k.range;
	    this.flags=k.flags;
	}

	public Key (Key k, int range) {
	    this.fn=k.fn;
	    this.s= new String(k.s);
	    this.range=range;
	    this.flags=k.flags;
	}

	public Key (String fn, String s, int range) {
	    this(fn, s, range, new BitSet(2));
	}

	public Key (String fn, String s) {
	    this(fn, s, 0, new BitSet(2));
	}


	public String toString() {
	    String ret = this.s;

	    // don't skip file name, add to beginning
	    if(!this.flags.get(0))
		ret = org.nyet.util.Files.filenameStem(this.fn) + ":" + ret;

	    // don't skip range #, add to end
	    if(!this.flags.get(1))
		ret += " " + (this.range+1);

	    return ret;
	}

	public void hideFilename() { this.flags.set(0); }
	public void showFilename() { this.flags.clear(0); }
	public void hideRange() { this.flags.set(1); }
	public void showRange() { this.flags.clear(1); }

	public String getFilename() { return this.fn; }
	public String getString() { return this.s; }
	public Integer getRange() { return this.range; }
	public void setRange(int r) { this.range=r; }

	public int compareTo(Object o) {
	    if(o instanceof Key) {
		final Key k = (Key)o;
		int out = this.fn.compareTo(k.fn);
		if(out!=0) return out;
		out = this.s.compareTo(k.s);
		if(out!=0) return out;
		return this.range.compareTo(k.range);
	    }
	    if(o instanceof String) {
		return this.s.compareTo((String)o);
	    }
	    throw new ClassCastException("Not a Key or a String!");
	}
	public boolean equals(Object o) {
	    if(o==null) return false;
	    if(o instanceof Key) {
		final Key k = (Key)o;
		if(!this.fn.equals(k.fn)) return false;
		if(!this.s.equals(k.s)) return false;
		return this.range.equals(k.range);
	    }
	    // if passed a string, only check the "s" portion
	    if(o instanceof String) {
		return this.s.equals((String)o);
	    }
	    throw new ClassCastException(o + ": Not a Key or a String!");
	}
    }

    public Dataset(String filename) throws Exception {
	this.fileId = org.nyet.util.Files.filename(filename);
	this.rows = 0;
	this.columns = new ArrayList<Column>();
	final CSVReader reader = new CSVReader(new FileReader(filename));
	ParseHeaders(reader);
	for(int i=0;i<this.ids.length;i++)
	    this.columns.add(new Column(this.ids[i], this.units[i]));

	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    for(int i=0;i<nextLine.length;i++) {
		if (this.columns.size() > i)
		    this.columns.get(i).add(nextLine[i]);
	    }
	    this.rows++;
	}
	buildRanges();
    }

    public ArrayList<Column> getColumns() {return this.columns;}

    public void ParseHeaders(CSVReader reader) throws Exception {
	this.ids = reader.readNext();
	this.units = new String[ids.length];
    }

    public Column get(int id) {
	return (Column) this.columns.get(id);
    }

    public String units(Comparable<?> id) {
	final Column c = this.get(id);
	if(c==null) return null;
	return c.getUnits();
    }

    public Column get(Comparable<?> id) {
	for(Column c : this.columns)
	    if(id.equals(c.id)) return c;
	return null;
    }

    public boolean exists(Comparable<?> id) {
	if (this.get(id) == null) return false;
	if (this.get(id).data == null) return false;
	if (this.get(id).data.size() == 0) return false;
	return true;
    }

    protected boolean dataValid(int i) { return true; }
    protected boolean rangeValid(Range r) { return true; }

    public ArrayList<Range> getRanges() {
	return this.range_cache;
    }

    protected void buildRanges() {
        this.range_cache = new ArrayList<Range>();
	Range r = null;
	for(int i=0;i<this.rows; i++) {
	    boolean end = false;
	    if(dataValid(i)) {
		if(r==null) {
		    r = new Range(i);
		    this.lastFilterReasons=new ArrayList<String>();
		}
		if(i==this.rows-1) end=true; // we hit end of data
	    } else {
		end = true;
	    }
	    if(r!=null && end) {
		r.end=i-1;
		if(rangeValid(r)) {
		    /*
		    if(this.lastFilterReasons.size()!=0) {
			System.out.println(Strings.join(":", this.lastFilterReasons) +
				": adding range " + r.toString());
		    }
		    */
		    this.range_cache.add(r);
		}
		r=null;
	    }
	}
    }

    public double[] getData(Key id, Range r) {
	// only match the string portion of the key
	final Column c = this.get(id.getString());
	if (c==null) return null;
	return c.data.toArray(r.start, r.end);
    }

    public double[] getData(Comparable<?> id, Range r) {
	final Column c = this.get(id);
	if (c==null) return null;
	return c.data.toArray(r.start, r.end);
    }

    public String getFileId() { return this.fileId; }

    public String [] getIds() { return this.ids; }
    public void setIds(String [] ids) { this.ids=ids; }
    public String [] getUnits() { return this.units; }
    public void setUnits(String [] units) { this.units=units; }

    public ArrayList<String> getLastFilterReasons() { return this.lastFilterReasons; }
    public int length() { return this.rows; }
}

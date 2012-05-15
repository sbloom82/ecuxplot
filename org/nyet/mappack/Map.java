package org.nyet.mappack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.nio.ByteBuffer;

import org.nyet.util.Strings;
import org.nyet.util.XmlString;

import org.nyet.logfile.CSVRow;


public class Map {
    // Inner classes
    private class Enm implements Comparable {
	protected int enm;
	public String[] legend;
	public Enm(ByteBuffer b) { enm=b.getInt(); }
	public Enm(int enm) { this.enm=enm; }
	public String toString() {
	    if(enm<0 || enm>legend.length-1)
		return String.format("(len %d) %x", legend.length, enm);
	    return legend[enm];
	}
	public int compareTo(Object o) {
	    return (new Integer(enm).compareTo(((Enm)o).enm));
	}
    }

    private class Organization extends Enm {
	public Organization(ByteBuffer b) {
	    super(b);
	    final String[] l= {
		"??",			// 0
		"??",			// 1
		"Single value",		// 2
		"Onedimensional",	// 3
		"Twodimensional",	// 4
		"2d Inverse"		// 5
	    };
	    legend = l;
	}
	public boolean isTable() {
	    return this.enm>2 && this.enm<6;
	}
	public boolean is1D() {
	    return this.enm == 3;
	}
    }

    public class ValueType extends Enm {
	private int width;
	public ValueType(int width, int enm) {
	    super(enm);
	    this.width = width;
	}
	public ValueType(ByteBuffer b) {
	    super(b);
	    final String[] l= {
		"??",				// 0
		"8 Bit",			// 1
		"16 Bit (HiLo)",		// 2
		"16 Bit (LoHi)",		// 3
		"32 Bit (HiLoHilo)",		// 4
		"32 Bit (LoHiLoHi)",		// 5
		"32 BitFloat (HiLoHiLo)",	// 6
		"32 BitFloat (LoHiLoHi)"	// 7
	    };
	    legend = l;
	    switch(this.enm) {
		case 1: width=1; break;
		case 2:
		case 3: width=2; break;
		case 4:
		case 5:
		case 6:
		case 7: width=4; break;
		default: width=0;
	    }
	}
	public boolean isLE() {
	    return (this.enm>1 && (this.enm & 1)==1);
	}
	public int width() { return this.width; }
    }

    private class DataSource extends Enm {
	private final String[] l = {
	    "1,2,3",		// 0
	    "Eprom",		// 1
	    "Eprom, add",		// 2
	    "Eprom, subtract",	// 3
	    "Free editable"		// 4
	};

	public DataSource(ByteBuffer b) {
	    super(b);
	    legend = l;
	}
	public DataSource() {
	    super(1);	// eeprom is default
	    legend = l;
	}
	public boolean isEeprom() {
	    return this.enm>0 && this.enm<4;
	}
	public boolean isOrdinal() {
	    return this.enm == 0;
	}
    }

    public class Dimension implements Comparable {
	public int x;
	public int y;
	public Dimension(int xx, int yy) { x = xx; y = yy; }
	public Dimension(ByteBuffer b) { x = b.getInt(); y = b.getInt(); }
	public String toString() { return x + "x" + y; }
	public int compareTo(Object o) {
	    return (new Integer(areaOf())).compareTo(((Dimension)o).areaOf());
	}
	public int areaOf() { return this.x*this.y; }
    }

    public class Value {
	public String description;
	public String units;
	public double factor;
	public double offset;

	public ValueType type=null;
	public int precision=0;
	public boolean sign=false;

	public String toString() {
	    return "(" + description + ")/" + units + " -  f/o: " + factor + "/" + offset;
	}

	// should only be called by derived classes
	protected Value(ByteBuffer b) throws ParserException {
	    description = Parse.string(b);
	    units = Parse.string(b);
	    factor = b.getDouble();
	    offset = b.getDouble();
	    // can't check precision until Valuetype, sign, precision are filled in
	}

	public Value(ByteBuffer b, ValueType vt, boolean s, int p) throws ParserException {
	    this(b);
	    type = vt;
	    sign = s;
	    precision = p;
	    limitPrecision(XDF_MaxDigits);
	}

	public double convert(double in) { return in*factor+offset; }

	public String eqOldXDF (int off, String tag) {
	    String out="";
	    if(this.factor != 1 || this.offset != 0) {
		out += String.format(XDF_LBL+"%f * X", off, tag, this.factor);
		if(this.offset!=0)
		    out += String.format("+ %f",this.offset);
		out+=",TH|0|0|0|0|\n";
	    }
	    return out;
	}

	public String eqXDF () {
	    String out="X";
	    if(this.factor != 1 || this.offset != 0) {
		out = String.format("%f * X", this.factor);
		if(this.offset!=0)
		    out += String.format("+ %f",this.offset);
	    }
	    return out;
	}

	protected int limitPrecision(int maxDigits) {
	    int width = this.sign?(this.type.width()*8)-1:this.type.width()*8;

	    // get maximum possible value
	    double max = convert(((1<<width)-1));

	    // digits left of decimal
	    int intdigits = (int)(Math.floor(Math.log10(max))+1);

	    // 99 = 2 digits, 100 = 3 digits
	    int digits = this.precision + intdigits;

	    if (digits>maxDigits)
		this.precision = maxDigits>intdigits?maxDigits - intdigits:0;

	    return this.precision;
	}

	protected void doMathXDF(XmlString xs) { doMathXDF(xs, this.eqXDF()); }
	protected void doMathXDF(XmlString xs, String x) {
	    xs.append("MATH equation=\"" + x + "\"");
	    xs.indent();
	    xs.append("VAR id=\"X\" /");
	    xs.unindent();
	    xs.append("/MATH");
	}
    }

    private class Axis {
        // passed
	private int size;
	private Dimension z_size;	/* for z axis - rows/cols */
	private String name;

	// parsed
	public Value value;
	public DataSource datasource;
	public HexValue addr = null;
	private int[] header1 = new int[2];	// unk
	private byte header1a;
	public boolean reciprocal = false;	// todo (find)
	private byte[] header2 = new byte[3];
	private int header3_size;
	private int[] header3;
	private int header4;
	public HexValue signature = null;

	private boolean isZ = false;

	public Axis(ByteBuffer b, String n, int s) throws ParserException {
	    value = new Value(b);
	    datasource = new DataSource(b);
	    // always read addr, so we advance pointer regardless of datasource
	    addr = new HexValue(b);
	    if(!datasource.isEeprom()) addr = null;
	    value.type = new ValueType(b);
	    Parse.buffer(b, header1);	// unk
	    header1a = b.get();		// unk
	    reciprocal = b.get()==1;
	    value.precision = b.get();
	    Parse.buffer(b, header2);	// unk
	    value.sign = (b.get()==1);
	    header3_size = b.getInt();		// unk
	    header3 = new int[header3_size/4];
	    Parse.buffer(b, header3);	// unk
	    header4 = b.getInt();		// unk
	    signature = new HexValue(b);

	    // fix precision last once we have the whole Value
	    value.limitPrecision(XDF_MaxDigits);
	    name = n;
	    size = s;
	}

	public Axis(Value v, HexValue a, Dimension d) {
	    value = v;
	    datasource = new DataSource();
	    addr = a;
	    name = "z";
	    size = 0;
	    z_size = d;
	    isZ = true;
	}

	public String toString() {
	    String out = super.toString() + "\n";
	    out += "\t   ds: " + datasource + "\n";
	    out += "\t addr: " + addr + " " + value.type + "\n";
	    out += "\t   h1: " + Arrays.toString(header1) + "\n";
	    out += "\t  h1a: " + header1a + " (short)\n";
	    out += "\tflags: ";
	    if(reciprocal) out += "R";
	    if(value.sign) out += "S";
	    out += "\n";
	    out += "\t prec: " + value.precision + " (byte)\n";
	    out += "\t   h2: " + Arrays.toString(header2) + "\n";
	    out += "\th3_sz: " + header3_size + "\n";
	    out += "\t   h3: " + Arrays.toString(header3) + "\n";
	    out += "\t   h4: " + header4 + "\n";
	    if(signature.v!=-1)
		out += "\t  sig: " + signature + "\n";
	    return out;
	}

	// Axis.toXDF()
	public String toXDF(XmlString xs) {
	    int xsAt=xs.length();

	    if (!isZ)
		xs.append("XDFAXIS id=\"" + this.name + "\" uniqueid=\"0x0\"");
	    else
		xs.append("XDFAXIS id=\"" + this.name + "\"");

	    xs.indent();

	    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
	    if(this.datasource.isOrdinal()) {
		m.put("mmedelementsizebits",16);
		m.put("mmedmajorstridebits",-32);
		xs.append("EMBEDDEDDATA",m);
		xs.append("units",this.value.units);
		xs.append("indexcount",this.size);

		if(this.value.precision!=2)
		    xs.append("decimalpl",this.value.precision);

		// outputtype 0x1 = float, 0x2 = integer, 0x4 = string
		xs.append("outputtype",this.value.precision==0?2:1);

		if (XDF_Pedantic) {
		    xs.append("datatype",0);
		    xs.append("unittype",0);
		    xs.append("DALINK index=\"0\" /");
		}

		genLabelsXDF(xs);
	    } else {
		int flags = this.value.sign?1:0;
		if (this.value.type.isLE()) flags |= 2;
		if (flags!=0)
		    m.put("mmedtypeflags",String.format("0x%02X", flags));
		m.put("mmedaddress",String.format("0x%X", this.addr.v));
		m.put("mmedelementsizebits", this.value.type.width()*8);

		if (isZ) {
		    m.put("mmedrowcount", z_size.y);
		    if (z_size.x>1)
			m.put("mmedcolcount", z_size.x);
		} else {
		    // weird. tunerpro puts mmedcolcount here sometimes, but not always
		    /*
		    if (flags!=0 && this.value.type.width()>1 && this.value.precision==2)
			m.put("mmedcolcount", this.size);
		    */
		    m.put("mmedmajorstridebits", this.value.type.width()*8);
		}
		xs.append("EMBEDDEDDATA",m);

		xs.append("units",this.value.units);
		if (this.size>0)
		    xs.append("indexcount",this.size);

		if(this.value.precision!=2)
		    xs.append("decimalpl",this.value.precision);

		// outputtype 0x1 = float, 0x2 = integer, 0x4 = string
		if (this.value.precision==0)
		    xs.append("outputtype",2);

		if (!isZ)
		    xs.append("embedinfo type=\"1\" /");

		if (XDF_Pedantic) {
		    if (!isZ) {
			xs.append("datatype", 0);
			xs.append("unittype", 0);
			xs.append("DALINK index=\"0\" /");
		    } else {
			xs.append("min","0.000000");
			if (this.value.type.width()==1)
			    xs.append("max","255.000000");
		    }
		}

		this.value.doMathXDF(xs);
	    }
	    xs.unindent();
	    xs.append("/XDFAXIS");
	    return xs.subSequence(xsAt, xs.length()).toString();
	}

	private void genLabelsXDF(XmlString xs) {
	    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
	    for(int i=0; i<this.size; i++) {
		m.put("index",i);
		if (this.size==1)
		    m.put("value",this.value.units);
		/*
		else if (this.value.precision==0)
		    m.put("value",String.format("%d",Math.round(value.convert(i))));
		*/
		else
		    m.put("value",String.format("%." + this.value.precision + "f",value.convert(i)));
		xs.append("LABEL",m);
	    }
	    if (XDF_Pedantic)
		this.value.doMathXDF(xs, "X");
	}
    }

    // Map Members
    private byte header0;
    public String name;
    public Organization organization;
    private int header;			//unk
    private int[] headera = new int[2];	// unk
    public int folderId;
    public String id;
    private int header1;		// unk
    private byte header1a;		// unk
    public int[] range = new int[4];
    private HexValue[] header2 = new HexValue[8];	// unk
    public boolean reciprocal;
    public boolean difference;
    public boolean percent;
    public Dimension size;
    private int[] header3 = new int[2];	// unk
    public Value value;
    public HexValue[] extent = new HexValue[2];
    private HexValue[] header4 = new HexValue[2];	// unk
    private int[] header5 = new int[2];	// unk
    private HexValue header6;
    private int header7;		// unk
    public Axis x_axis;
    public Axis y_axis;
    public Axis z_axis;
    private int header8;		// unk
    private short header8a;		// unk
    private int[] header9 = new int[8];	// unk
    private short header9a;		// unk
    private int header9b;		// unk
    private byte header9c;		// unk
    private HexValue[] header10 = new HexValue[6];// unk
    private HexValue[] header11 = new HexValue[2];// unk
    public byte[] term2 = new byte[3];

    // Map Constructors
    public Map(ByteBuffer b) throws ParserException {
	header0 = b.get();		// unk
	name = Parse.string(b);
	organization = new Organization(b);
	header = b.getInt();
	ValueType vt = new ValueType(b);
	Parse.buffer(b, headera);	// unk
	folderId = b.getInt();
	id = Parse.string(b);
	header1 = b.getInt();		// unk
	header1a = b.get();		// unk
	Parse.buffer(b, range);
	Parse.buffer(b, header2);	// unk
	reciprocal = b.get()==1;
	boolean sign = (b.get()==1);
	difference = b.get()==1;
	percent = b.get()==1;
	size = new Dimension(b);
	Parse.buffer(b, header3);	// unk
	int precision = b.getInt();
	value = new Value(b, vt, sign, precision);
	Parse.buffer(b, extent);
	Parse.buffer(b, header4);	// unk
	Parse.buffer(b, header5);	// unk
	header6 = new HexValue(b);
	header7 = b.getInt();
	x_axis = new Axis(b, "x", size.x);
	y_axis = new Axis(b, "y", size.y);
	header8 = b.getInt();		// unk
	header8a = b.getShort();	// unk
	Parse.buffer(b, header9);	// unk
	header9a = b.getShort();	// unk
	header9b = b.getInt();		// unk
	header9c = b.get();		// unk
	Parse.buffer(b, header10);	// unk
	Parse.buffer(b, header11);	// unk
	b.get(term2);
	z_axis = new Axis(this.value, this.extent[0], this.size);
	// System.out.println(this);
    }

    // generate a 1d map for an axis
    public Map(Axis axis, int size) {
	this.extent[0] = axis.addr;
	this.value = axis.value;
	this.size = new Dimension(1, size);
    }

    // Map methods
    public static final String CSVHeader() {
	final String[] header = {
	    "ID","Address","Name","Size","Organization","Description",
	    "Units","X Units","Y Units",
	    "Scale","X Scale","Y Scale",
	    "Value min","Value max","Value min*1", "Value max*1"
	    };
	final CSVRow out = new CSVRow(header);
	return out.toString();
    }

    public boolean equals(Map map) {
	String stem=map.id.split("[? ]")[0];
	if(stem.length()==0) return false;
	return equals(stem);
    }

    public boolean equals(String id) {
	if(id.length()==0 || this.id.length() == 0) return false;
	return (id.equals(this.id.split("[? ]")[0]));
    }


    public static final int FORMAT_DUMP = 0;
    public static final int FORMAT_CSV = 1;
    public static final int FORMAT_OLD_XDF = 2;
    public static final int FORMAT_XDF = 3;
    public static final boolean XDF_Pedantic = false;
    public static final int XDF_MaxDigits = 6;	// tunerpro doesn't like > 6 digits
    public static final String XDF_LBL = "\t%06d %-17s=";
    public String toString(int format, ByteBuffer image)
	throws Exception {
	switch(format) {
	    case FORMAT_DUMP: return toString();
	    case FORMAT_CSV: return toStringCSV(image);
	    case FORMAT_OLD_XDF: return toStringOldXDF(image);
	    case FORMAT_XDF: return toStringXDF(image);
	}
	return "";
    }

    private String toStringCSV(ByteBuffer image) throws Exception {
	CSVRow row = new CSVRow();
	row.add(this.id);
	row.add(this.extent[0]);
	row.add(this.name);
	row.add(this.size);
	row.add(this.value.type);
	row.add(this.value.description);
	row.add(this.value.units);
	row.add(this.x_axis.value.units);
	row.add(this.y_axis.value.units);
	row.add(this.value.factor);
	row.add(this.x_axis.value.factor);
	row.add(this.y_axis.value.factor);
	if(image!=null && image.limit()>0) {
	    MapData mapdata = new MapData(this, image);
	    row.add(mapdata.getMinimumValue());
	    row.add(mapdata.getMaximumValue());
	    row.add(String.format("0x%x", mapdata.getMinimum()));
	    row.add(String.format("0x%x", mapdata.getMaximum()));
	} else {
	    row.add("");
	    row.add("");
	}
	return row.toString();
    }

    private static String ordinalArray(int len) {
	Integer out[] = new Integer[len];
	for(int i=0;i<len;i++) out[i]=i+1;
	return Strings.join(",", out);
    }

    private String toStringOldXDF(ByteBuffer image) throws Exception {
	boolean table = this.organization.isTable();
	boolean oneD = this.organization.is1D() || this.size.y<=1;
	String out = table?"%%TABLE%%\n":"%%CONSTANT%%\n";
	out += String.format(XDF_LBL+"0x%X\n",100,"Cat0ID",this.folderId+1);
	int off = table?40000:20000;
	String title = "";
	String desc = "";
	if(this.id.length()>0) {
	    title = this.id.split(" ")[0];	// HACK: drop the junk
	    desc = this.name;
	} else {
	    title = this.name;
	}

	out += String.format(XDF_LBL+"\"%s\"\n",off+5,"Title",title);

	if(desc.length()>0) {
	    out += String.format(XDF_LBL+"\"%s\"\n",off+10,"Desc",desc);
	    out += String.format(XDF_LBL+"0x%X\n",off+11,"DescSize",
		    desc.length()+1);
	}

	if(this.value.units.length()>0) {
	    if(table)
		out += String.format(XDF_LBL+"\"%s\"\n",off+330,"ZUnits",
			this.value.units);
	    else
		out += String.format(XDF_LBL+"\"%s\"\n",off+20,"Units",
			this.value.units);
	}

	if(this.value.type.width()>1) {
	    out += String.format(XDF_LBL+"0x%X\n",off+50,"SizeInBits",
		    this.value.type.width()*8);
	}

	if(this.value.precision!=2) {
	    out += String.format(XDF_LBL+"0x%X\n",off+210,"DecimalPl",
		    this.value.precision);
	}

	// XDF "Flags"
	// -----------
	// 0x001 - z value signed
	// 0x002 - z value LE
	// 0x040 - x axis signed
	// 0x100 - x axis LE
	// 0x080 - y axis signed
	// 0x200 - y axis LE

	int flags = this.value.sign?1:0;
	if (this.value.type.isLE()) flags |= 2;

	out += String.format(XDF_LBL+"0x%X\n",off+100,"Address",
		this.extent[0].v);

	out += this.value.eqOldXDF(off+200, table?"ZEq":"Equation");

	if(table) {
	    if (this.size.x > 0x100 && this.size.y <= 0x100) {
		// swap x and y; tunerpro crashes on Cols > 256
		Axis tmpa = this.y_axis;
		this.y_axis = this.x_axis;
		this.x_axis = tmpa;

		this.x_axis.name = "x";
		this.y_axis.name = "y";

		int tmp = this.size.y;
		this.size.y = this.size.x;
		this.size.x = tmp;
	    }
	    // X (columns)
	    if (this.x_axis.value.sign) flags |= 0x40;
	    if (this.x_axis.value.type.isLE()) flags |= 0x100;

	    // 300s
	    out += String.format(XDF_LBL+"0x%X\n", off+305, "Cols",
		this.size.x);
	    out += String.format(XDF_LBL+"\"%s\"\n", off+320, "XUnits",
		this.x_axis.value.units);
	    out += String.format(XDF_LBL+"0x%X\n", off+352,
		"XLabelType", x_axis.value.precision==0?2:1);

	    if(this.x_axis.datasource.isOrdinal() && this.size.x>1) {
		out += String.format(XDF_LBL+"%s\n", off+350, "XLabels",
			ordinalArray(this.size.x));
		out += String.format(XDF_LBL+"0x%X\n", off+352, "XLabelType", 2);
	    } else if(this.x_axis.addr!=null) {
		out += this.x_axis.value.eqOldXDF(off+354, "XEq");
		// 500s
		out += String.format(XDF_LBL+"0x%X\n", off+505, "XLabelSource", 1);
		// 600s
		out += String.format(XDF_LBL+"0x%X\n", off+600, "XAddress",
		    this.x_axis.addr.v);
		out += String.format(XDF_LBL+"%d\n", off+610, "XDataSize",
		    this.x_axis.value.type.width());
		out += String.format(XDF_LBL+"%d\n", off+620, "XAddrStep",
		    this.x_axis.value.type.width());
		if(x_axis.value.precision!=2) {
		    out += String.format(XDF_LBL+"0x%X\n", off+650,
			"XOutputDig", x_axis.value.precision);
		}
	    }

	    // Y (rows)
	    if (this.y_axis.value.sign) flags |= 0x80;
	    if (this.y_axis.value.type.isLE()) flags |= 0x200;

	    // 300s
	    out += String.format(XDF_LBL+"0x%X\n", off+300, "Rows",
		this.size.y);
	    out += String.format(XDF_LBL+"\"%s\"\n", off+325, "YUnits",
		this.y_axis.value.units);
	    // LabelType 0x1 = float, 0x2 = integer, 0x4 = string
	    out += String.format(XDF_LBL+"0x%X\n", off+362,
		"YLabelType", y_axis.value.precision==0?2:1);

	    if(this.y_axis.datasource.isOrdinal() && this.size.y>1 ) {
		out += String.format(XDF_LBL+"%s\n", off+360, "YLabels",
			ordinalArray(this.size.y));
		out += String.format(XDF_LBL+"0x%X\n", off+362, "YLabelType", 2);
	    } else if(this.y_axis.addr!=null) {
		out += this.y_axis.value.eqOldXDF(off+364, "YEq");
		// 500s
		out += String.format(XDF_LBL+"0x%X\n", off+515, "YLabelSource", 1);
		// 700s
		out += String.format(XDF_LBL+"0x%X\n", off+700, "YAddress",
		    this.y_axis.addr.v);
		out += String.format(XDF_LBL+"%d\n", off+710, "YDataSize",
		    this.y_axis.value.type.width());
		out += String.format(XDF_LBL+"%d\n", off+720, "YAddrStep",
		    this.y_axis.value.type.width());
		if(y_axis.value.precision!=2) {
		    out += String.format(XDF_LBL+"0x%X\n", off+750,
			"YOutputDig", y_axis.value.precision);
		}
	    }
	}
	out += String.format(XDF_LBL+"0x%X\n",off+150,"Flags", flags);

	if(false && image!=null && image.limit()>0) {
	    MapData mapdata = new MapData(this, image);
	    if(table && this.x_axis.addr!=null) {
		MapData xaxis = new MapData(new Map(this.x_axis, this.size.x),
			image);
		out += String.format(XDF_LBL+"%s\n", off+350, "XLabels",
			xaxis.toString());
		// LabelType 0x1 = float, 0x2 = integer, 0x4 = string
		out += String.format(XDF_LBL+"0x%X\n", off+352,
		    "XLabelType", x_axis.value.precision==0?2:1);
		if(!oneD && this.y_axis.addr!=null) {
		    MapData yaxis = new MapData(new Map(this.y_axis,
				this.size.y), image);
		    out += String.format(XDF_LBL+"%s\n", off+360, "YLabels",
			    yaxis.toString());
		}
	    }
	    /*
	    out += String.format(XDF_LBL+"%f\n", off+230, "RangeLow",
		mapdata.getMinimumValue());
	    out += String.format(XDF_LBL+"%f\n", off+240, "RangeHigh",
		mapdata.getMaximumValue());
	    */
	}

	if(oneD) {
	    // LabelType 0x1 = float, 0x2 = integer, 0x4 = string
	    out += String.format(XDF_LBL+"%s\n", off+360, "YLabels",
		this.y_axis.value.units);
	    out += String.format(XDF_LBL+"0x%X\n", off+362,
		"YLabelType", 4);
	}

	return out + "%%END%%\n";
    }

    private void tableToXDF(XmlString xs) {
	int flags = this.value.sign?1:0;
	if (this.value.type.isLE()) flags |= 2;

	if (this.size.x > 0x100 && this.size.y <= 0x100) {
	    // swap x and y; tunerpro crashes on Cols > 256
	    Axis tmpa = this.y_axis;
	    this.y_axis = this.x_axis;
	    this.x_axis = tmpa;

	    this.x_axis.name = "x";
	    this.y_axis.name = "y";

	    int tmp = this.size.y;
	    this.size.y = this.size.x;
	    this.size.x = tmp;
	}

	this.x_axis.toXDF(xs);
	this.y_axis.toXDF(xs);
	this.z_axis.toXDF(xs);
    }

    private void constantToXDF(XmlString xs) {
	int flags = this.value.sign?1:0;
	if (this.value.type.isLE()) flags |= 2;

	LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
	if (flags!=0)
	    m.put("mmedtypeflags",String.format("0x%02X", flags));
	m.put("mmedaddress",String.format("0x%X", this.extent[0].v));
	m.put("mmedelementsizebits", this.value.type.width()*8);

	xs.append("EMBEDDEDDATA",m);
	xs.append("units",this.value.units);
	if(this.value.precision!=2)
	    xs.append("decimalpl", this.value.precision);

	if (XDF_Pedantic) {
	    xs.append("datatype",0);
	    xs.append("unittype",0);
	    xs.append("DALINK index=\"0\" /");
	}

	this.value.doMathXDF(xs);
    }

    private String toStringXDF(ByteBuffer image) throws Exception {
	boolean table = this.organization.isTable();
	String out, tag;

	XmlString xs = new XmlString(1);
	if (table) {
	    tag = "XDFTABLE";
	    xs.append("XDFTABLE uniqueid=\"0x0\" flags=\"0x0\"");
	} else {
	    tag = "XDFCONSTANT";
	    xs.append("XDFCONSTANT uniqueid=\"0x0\"");
	}
	xs.indent();

	String title = "";
	String desc = "";
	if(this.id.length()>0) {
	    title = this.id.split(" ")[0];	// HACK: drop the junk
	    desc = this.name;
	} else {
	    title = this.name;
	}
	xs.append("title", title);
	xs.append("description", desc);
	xs.append("CATEGORYMEM index=\"0\" category=\"" + (this.folderId+1) + "\" /");

	if(table) tableToXDF(xs);
	else constantToXDF(xs);

	xs.unindent();
	return xs.append("/" + tag).toString();
    }

    public String toString() {
	String out = "   h0: " + header0 + "\n";
	out += "  map: " + name + " [" + id + "] " + value.type + "\n";
	out += "  org: " + organization + "\n";
	out += "    h: " + header + "\n";
	out += "   ha: " + Arrays.toString(headera) + "\n";
	out += "fdrId: " + folderId + "\n";
	out += "   h1: " + header1 + "\n";
	out += "  h1a: " + header1a + " (byte)\n";
	out += "range: " + range[0] + "-" + range[2]+ "\n";
	out += "   h2: " + Arrays.toString(header2) + "\n";
	out += "flags: ";
	if(reciprocal) out += "R";
	if(value.sign) out += "S";
	if(difference) out += "D";
	if(percent) out += "P";
	out += "\n";
	out += " size: " + size + "\n";
	out += "   h3: " + Arrays.toString(header3) + "\n";
	out += " prec: " + value.precision + "\n";
	out += "value: " + value + "\n";
	out += " addr: " + Arrays.toString(extent) + "\n";
	out += "   h4: " + Arrays.toString(header4) + "\n";
	out += "   h5: " + Arrays.toString(header5) + "\n";
	out += "   h6: " + header6 + "\n";
	out += "   h7: " + header7 + "\n";
	out += "xaxis: " + x_axis + "\n";
	out += "yaxis: " + y_axis + "\n";
	out += "   h8: " + header8 + "\n";
	out += "  h8a: " + header8a + " (short)\n";
	out += "   h9: " + Arrays.toString(header9) + "\n";
	out += "  h9a: " + header9a + " (short)\n";
	out += "  h9b: " + header9b + "\n";
	out += "  h9c: " + header9c + " (byte)\n";
	out += "  h10: " + Arrays.toString(header10) + "\n";
	out += "  h11: " + Arrays.toString(header11) + "\n";
	out += " term2: " + Arrays.toString(term2) + "\n";
	return out;
    }
}

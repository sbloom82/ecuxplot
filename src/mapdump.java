import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.PrintStream;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;

import org.nyet.mappack.*;
import org.nyet.util.*;

public class mapdump {
    private static class MapDumpOptions extends Options {

	String input = null;
	PrintStream output = System.out;
	String[] refs = new String[0];
	String image = null;
	int format = Map.FORMAT_CSV;

	public MapDumpOptions() {
	    Option r = OptionBuilder.withArgName("maps.kp [...]").hasArg().hasOptionalArgs()
		.withDescription(
		    "Annotate with descriptions from matching maps also in these mappacks (ignored if -x is used)")
		.create('r');
	    Option i = OptionBuilder.withArgName("image.bin").hasArg()
		.withDescription(
		    "Generate min/max columns and image size based on this image")
		.create('i');

	    Option d = new Option("d", "Generate raw dump");
	    Option o = new Option("o", "Generate old xdf (requires -i <image.bin>)");
	    Option x = new Option("x", "Generate xml xdf (requires -i <image.bin>)");

	    this.addOption(r);
	    this.addOption(i);

	    this.addOption(d);
	    this.addOption(o);
	    this.addOption(x);
	}

	public void Parse(String args[]) throws ParseException {
	    CommandLineParser parser = new BasicParser(); 
	    CommandLine line = parser.parse(this, args);

	    if (line.hasOption('r')) {
		this.refs = line.getOptionValues("r");
	    }

	    if (line.hasOption('i')) {
		this.image = line.getOptionValue("i");
	    }

	    if (line.hasOption('d')) {
		this.format = Map.FORMAT_DUMP;
	    }

	    if (line.hasOption('o')) {
		if(this.format != Map.FORMAT_CSV) {
		    throw new ParseException("Can only specify one of '-d', '-o', '-x'");
		}
		if (this.image == null) {
		    throw new ParseException("-o requires -i <image.bin> to detect image size");
		}
		this.format = Map.FORMAT_OLD_XDF;
	    }

	    if (line.hasOption('x')) {
		if(this.format != Map.FORMAT_CSV) {
		    throw new ParseException("Can only specify one of '-d', '-o', '-x'");
		}
		if (this.image == null) {
		    throw new ParseException("-x requires -i <image.bin> to detect image size");
		}
		this.format = Map.FORMAT_XDF;
	    }

	    String left[] = line.getArgs();
	    if (left.length<=0) {
		throw new ParseException("You must specify an input filename");
	    }

	    this.input = left[0];

	    if (left.length>1) {
		try {
		    this.output = new PrintStream(left[1]);
		} catch (Exception e) {
		    throw new ParseException("Can't open '" + left[1] + "' for writing:\n  "
			+ e.getMessage());
		}
	    }

	    if (left.length>2) {
		throw new ParseException("Too many arguments");
	    }
	}

	public String Usage() {
	    StringWriter sw = new StringWriter();
	    HelpFormatter formatter = new HelpFormatter();
	    formatter.printOptions(new PrintWriter(sw), 80, this, 1, 3);
	    return 
		  "Usage: mapdump [options] maps.kp [outputfile]\n"
		+ "Options:\n"
		+ sw.getBuffer().toString();
	}
    }

    public static void main(String[] args) throws Exception
    {
	MapDumpOptions opts = new MapDumpOptions();

	try {
	    opts.Parse(args);
	} catch (ParseException e) { 
	    System.err.println(e.getMessage());
	    System.err.println(opts.Usage());
	    return;
	}

	Parser mp = new Parser(opts.input);
	ArrayList<Parser> refs = new ArrayList<Parser>();
	ByteBuffer imagebuf=null;
	String refsHeader="";
	for(String s: opts.refs) {
	    refs.add(new Parser(s));
	    refsHeader+=",\"" + s + "\"";
	}
	if(opts.image!=null) {
	    MMapFile mmap = new MMapFile(opts.image, ByteOrder.LITTLE_ENDIAN);
	    imagebuf = mmap.getByteBuffer();
	}
	switch(opts.format) {
	    case Map.FORMAT_CSV:
		opts.output.print(Map.CSVHeader()+refsHeader);
		opts.output.println();
		break;
	    case Map.FORMAT_OLD_XDF:
		opts.output.print("XDF\n1.110000\n\n");
		break;
	    case Map.FORMAT_XDF:
		Date date = new Date();
		opts.output.print("<!-- Written " + date.toString() + " -->\n");
		opts.output.print("<XDFFORMAT version=\"1.50\">\n");
		break;
	    default: break;
	}
	for(Project p: mp.projects) {
	    opts.output.print(p.toString(opts.format, imagebuf));
	    /*
	    for(Folder f: p.folders) {
		System.err.print(f.toString(opts.format));
		System.err.println();
	    }
	    */
	    if (p.maps==null) continue;

	    for(Map m: p.maps) {
		opts.output.print(m.toString(opts.format, imagebuf));
		if(opts.format == Map.FORMAT_CSV) {
		    for(Parser pa: refs) {
			ArrayList<Map> matches = pa.find(m);
			if(matches.size()>0) {
			    Map r = matches.get(0);
			    opts.output.print(",\"" + r.name + "\"");
			} else {
			    opts.output.print(",\"\"");
			}
		    }
		    opts.output.println();
		}
	    }
	}
	if (opts.format==Map.FORMAT_XDF)
	    opts.output.print("</XDFFORMAT>\n");
    }
}

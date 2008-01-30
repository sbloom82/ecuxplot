import java.io.File;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.*;

import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JCheckBox;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;

import org.jfree.data.time.Month;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;

import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import org.nyet.logfile.CSVFileFilter;
import org.nyet.logfile.Dataset;
import org.nyet.util.WaitCursor;
import org.nyet.util.EChartFactory;
import org.nyet.util.MenuListener;
import org.nyet.util.SubActionListener;

public class ECUxPlot extends ApplicationFrame implements SubActionListener {
    private Dataset dataSet;
    private ChartPanel chart;
    private JMenuBar menuBar;

    private static JMenu[] createMenuTriplet(String label)
    {
	JMenu[] out={
	    new JMenu(label),
	    new JMenu(label),
	    new JMenu(label)};
	return out;
    }

    private static JMenu[] createMenuTriplet(String[] label)
    {
	JMenu[] out={
	    new JMenu(label[0]),
	    new JMenu(label[1]),
	    new JMenu(label[2])};
	return out;
    }

    private static void addMenuTriplet(JMenu[] dest, AbstractButton[] src) {
	dest[0].add(src[0]);
	dest[1].add(src[1]);
	dest[2].add(src[2]);
    }

    private void setupAxisMenus(String[] headers) {
	if(headers.length<=0) return;

	String[] menus = {"X Axis", "Y Axis", "Y Axis2"};
	JMenu[] triplet = createMenuTriplet(menus);

	MenuListener[] ltriplet = {
	    new MenuListener(this, menus[0]),
	    new MenuListener(this, menus[1]),
	    new MenuListener(this, menus[2])};

	menuBar.add(triplet[0]);
	menuBar.add(triplet[1]);
	menuBar.add(triplet[2]);

	ButtonGroup bg = new ButtonGroup();
	JMenu[] boost = null;
	JMenu[] ignition = null;
	JMenu[] knock = null;
	JMenu[] load = null;
	JMenu[] egt = null;

	for(int i=0;i<headers.length;i++) {
	    AbstractButton[] item = {
		new JRadioButtonMenuItem(headers[i], headers[i].equals("TIME")),
		new JCheckBox(headers[i],headers[i].equals("RPM")),
		new JCheckBox(headers[i],headers[i].equals("EngineLoad"))};

	    item[0].addActionListener(ltriplet[0]);
	    item[1].addActionListener(ltriplet[1]);
	    item[2].addActionListener(ltriplet[2]);

	    bg.add(item[0]);

	    if(headers[i].matches("^Boost.*")) {
		if(boost==null) {
		    boost=createMenuTriplet("Boost...");
		    addMenuTriplet(triplet, boost);
		}
		addMenuTriplet(boost, item);
	    } else if(headers[i].matches("^Ignition.*")) {
		if(ignition==null) {
		    ignition=createMenuTriplet("Ignition...");
		    addMenuTriplet(triplet, ignition);
		}
		addMenuTriplet(ignition, item);
	    } else if(headers[i].matches("^Knock.*")) {
		if(knock==null) {
		    knock=createMenuTriplet("Knock...");
		    addMenuTriplet(triplet, knock);
		}
		addMenuTriplet(knock, item);
	    } else if(headers[i].matches(".*Load.*")) {
		if(load==null) {
		    load=createMenuTriplet("Load...");
		    addMenuTriplet(triplet, load);
		}
		addMenuTriplet(load, item);
	    } else if(headers[i].matches("^EGT.*")) {
		if(egt==null) {
		    egt=createMenuTriplet("EGT...");
		    addMenuTriplet(triplet, egt);
		}
		addMenuTriplet(egt, item);
	    } else {
		addMenuTriplet(triplet, item);
	    }
	}
    }

    private void setXAxis(String id) {
	System.out.println("xaxis " + id);
    }
    private void setYAxis(String id, boolean on) {
	System.out.println("yaxis " + id);
    }
    private void setYAxis2(String id, boolean on) {
	System.out.println("yaxis2 " + id);
    }
    public void actionPerformed(ActionEvent event) {
	AbstractButton source = (AbstractButton) (event.getSource());
	if(source.getText().equals("Quit")) {
	    System.exit(0);
	} else if(source.getText().equals("Close Chart")) {
	    this.dispose();
	} else if(source.getText().equals("New Chart")) {
	    final ECUxPlot plot = new ECUxPlot("ECUxPlot");
	    plot.pack();
	    Point where = this.getLocation();
	    where.translate(20,20);
	    plot.setLocation(where);
	    plot.setVisible(true);
	} else if(source.getText().equals("Open File")) {
	    //final JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
	    final JFileChooser fc = new JFileChooser();
	    fc.setFileFilter(new CSVFileFilter());
	    int ret = fc.showOpenDialog(this);
	    if(ret == JFileChooser.APPROVE_OPTION) {
		File file = fc.getSelectedFile();
		WaitCursor.startWaitCursor(this);
		try {
		    dataSet = new Dataset(file.getAbsolutePath());
		    chart = CreateChartPanel(dataSet);
		    setContentPane(chart);
		    this.setTitle("ECUxPlot " + file.getName());
		    setupAxisMenus(dataSet.headers);
		} catch (Exception e) {
		    JOptionPane.showMessageDialog(this, e.getMessage());
		}
		WaitCursor.stopWaitCursor(this);
	    }	
	}
    }

    public void actionPerformed(ActionEvent event, Comparable id) {
	AbstractButton source = (AbstractButton) (event.getSource());
	if(id.equals("X Axis")) {
	    EChartFactory.setChartX(chart, dataSet, source.getText());
	} else if(id.equals("Y Axis")) {
	    EChartFactory.editChartY(chart, dataSet, source.getText(),0,source.isSelected());
	} else if(id.equals("Y Axis2")) {
	    EChartFactory.editChartY(chart, dataSet, source.getText(),1,source.isSelected());
	}
    }

    private final void PopulateFileMenu(JMenu filemenu) {
	JMenuItem openitem = new JMenuItem("Open File");
	openitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_O, ActionEvent.CTRL_MASK));
	openitem.addActionListener(this);
	filemenu.add(openitem);

	filemenu.add(new JSeparator());

	JMenuItem newitem = new JMenuItem("New Chart");
	newitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_N, ActionEvent.CTRL_MASK));
	newitem.addActionListener(this);
	filemenu.add(newitem);

	JMenuItem closeitem = new JMenuItem("Close Chart");
	closeitem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_W, ActionEvent.CTRL_MASK | ActionEvent.SHIFT_MASK));
	closeitem.addActionListener(this);
	filemenu.add(closeitem);

	filemenu.add(new JSeparator());

	JMenuItem quititem = new JMenuItem("Quit");
	quititem.setAccelerator(KeyStroke.getKeyStroke(
	    KeyEvent.VK_F4, ActionEvent.ALT_MASK));
	quititem.addActionListener(this);
	filemenu.add(quititem);
    }

    public ECUxPlot(final String title) {
        super(title);

	menuBar = new JMenuBar();

	/* File Menu */
	JMenu filemenu = new JMenu("File");
	PopulateFileMenu(filemenu);
	menuBar.add(filemenu);

	setJMenuBar(menuBar);

	setPreferredSize(new java.awt.Dimension(800,600));
    }

    private static ChartPanel CreateChartPanel(Dataset data) throws Exception {

	final String[] what = {"TIME", "RPM", "EngineLoad"};
	final String xAxisLegend = "TIME";

        final XYDataset dataset1 = EChartFactory.createDataset(data, what[0], what[1]);
	final String yAxisLegend = "RPM";

        final XYDataset dataset2 = EChartFactory.createDataset(data, what[0], what[2]);
	final String y2AxisLegend = "%";

        final JFreeChart chart = EChartFactory.create2AxisXYLineChart(
            what[0] + " and " + what[1],
	    xAxisLegend, yAxisLegend, y2AxisLegend,
            dataset1, dataset2,
	    PlotOrientation.VERTICAL,
            true,	// show legend
            true,	// show tooltips
            false	// show urls
        );

        return new ChartPanel(chart);
    }

    public static void main(final String[] args) {
	final ECUxPlot plot = new ECUxPlot("ECUxPlot");
	plot.pack();
	RefineryUtilities.centerFrameOnScreen(plot);
	plot.setVisible(true);
    }
}

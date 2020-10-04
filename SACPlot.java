import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.Properties;
import jssc.SerialPortList;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.serial.*;
import sacformat.SacTimeSeries;

public class SACPlot {
    static Dimension size = new Dimension(1280, 500);

    static BufferedImage img;
    static SacTimeSeries data = new SacTimeSeries();
    static JPanel view;
    static SimRunner simrun;

    // draw the image
    static void drawImage(float[] samples) {
        Graphics2D g2d = img.createGraphics();

        int subsetLength = samples.length / size.width;

        float[] subsets = new float[size.width];

        // find average(abs) of each box subset
        int s = 0;
        for(int i = 0; i < subsets.length; i++) {

            double sum = 0;
            for(int k = 0; k < subsetLength; k++) {
                sum += Math.abs(samples[s++]);
            }

            subsets[i] = (float)(sum / subsetLength);
        }

        // find the peak so the waveform can be normalized
        // to the height of the image
        float normal = 0;
        for(float sample : subsets) {
            if(sample > normal)
                normal = sample;
        }

        // normalize and scale
        normal = 32768.0f / normal;
        for(int i = 0; i < subsets.length; i++) {
            subsets[i] *= normal;
            subsets[i] = (subsets[i] / 32768.0f) * (size.height / 2);
        }

        g2d.setColor(Color.GRAY);

        // convert to image coords and do actual drawing
        for(int i = 0; i < subsets.length; i++) {
            int sample = (int)subsets[i];

            int posY = (size.height / 2) - sample;
            int negY = (size.height / 2) + sample;

            int x = i;

            g2d.drawLine(x, posY, x, negY);
        }

        g2d.dispose();
        view.repaint();
        view.requestFocus();
    }

    static void loadImage() {
        JFileChooser chooser = new JFileChooser(".");
        int val = chooser.showOpenDialog(null);
        if(val != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        try {
            File file = chooser.getSelectedFile();
            data.read(file.getName());
        } catch(Exception e) {
            e.printStackTrace();
        }

        img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);

        drawImage(data.y);
    }

    static void problem(Object msg) {
        JOptionPane.showMessageDialog(null, String.valueOf(msg));
    }

    public static void main(String[] args) throws Exception {
        ModbusMaster m = null;
        SerialParameters sp = loadSerialParameters();
        SerialUtils.setSerialPortFactory(new SerialPortFactoryJSSC());
        ModbusMaster m = ModbusMasterFactory.createModbusMasterRTU(sp);
        m.connect();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Quake Sim");
                JTabbedPane tabbedPane = new JTabbedPane();
                JPanel simulator = new JPanel(new BorderLayout());
                JPanel mtcontrol = new JPanel(new BorderLayout());
                tabbedPane.addTab("Simulator", null, simulator,
                  "Shake table at extracted frequency");
                tabbedPane.addTab("Motor Control", null, mtcontrol,
                  "Direct motor control");
                frame.setContentPane(tabbedPane);

                JButton load = new JButton("Load");
                JScrollBar motorFreq = new JScrollBar(JScrollBar.HORIZONTAL, 500, 60, 500, 5000);
                JButton startMotor = new JButton("Start Motor");
                JButton stopMotor = new JButton("Stop/Init Motor");
                JButton startSim = new JButton("Start Simulation");
                JButton stopSim = new JButton("Stop Simulation");
                int slaveId = 1;
                load.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        loadImage();
                        startSim.setEnabled(true);
                    }
                });
                
                stopSim.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        tabbedPane.setEnabledAt(1, true);
                        stopSim.setEnabled(false);
                        load.setEnabled(true);
                        simrun.stopSim();
                        simrun = null;
                    }
                });
                
                startSim.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        tabbedPane.setEnabledAt(1, false);
                        stopSim.setEnabled(true);
                        load.setEnabled(false);
                        if(simrun == null) simrun = new SimRunner(m,data);
                        if(!simrun.isAlive()) simrun.start();
                    }
                });
                
                motorFreq.addAdjustmentListener(new AdjustmentListener() {
                    @Override
                    public void adjustmentValueChanged(AdjustmentEvent ev) {
                        if(ev.getValueIsAdjusting()) return;
                        while(true) {
                            try {
                                System.out.println(ev.getValue());
                                m.writeSingleRegister(slaveId, 100, motorFreq.getValue());
                                break;
                            } catch(Exception ex) {}
                        }
                        try {
                            Thread.sleep(80);
                        } catch (Exception ex) {}
                    }
                });
                motorFreq.setEnabled(false);
                
                startMotor.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        while(true) {
                            try {
                                m.writeSingleRegister(slaveId, 99, 1151); // control in ON
                                break;
                            } catch(Exception ex) {}
                        }
                        try {
                            Thread.sleep(80);
                        } catch (Exception e) {}
                    }
                });
                
                stopMotor.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        while(true) {
                            try {
                                m.writeSingleRegister(slaveId, 99, 1150); // start control in OFF state
                                break;
                            } catch(Exception ex) {}
                        }
                        try {
                            Thread.sleep(80);
                        } catch (Exception e) {}
                        while(true) {
                            try {
                                m.writeSingleRegister(slaveId, 100, 800); // set freq% to 800
                                break;
                            } catch(Exception ex) {}
                        }
                        try {
                            Thread.sleep(80);
                        } catch (Exception e2) {}
                        startMotor.setEnabled(true);
                        motorFreq.setEnabled(true);
                    }
                });
                startMotor.setEnabled(false);
                
                startSim.setEnabled(false);
                
                stopSim.setEnabled(false);

                view = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);

                        if(img != null) {
                            g.drawImage(img, 1, 1, img.getWidth(), img.getHeight(), null);
                        }
                    }
                };

                view.setBackground(Color.WHITE);
                view.setPreferredSize(new Dimension(size.width + 2, size.height + 2));
                
                JPanel filler = new JPanel();
                filler.setPreferredSize(new Dimension(size.width + 2, size.height + 2));

                simulator.add(view, BorderLayout.PAGE_START);
                simulator.add(load, BorderLayout.LINE_START);
                simulator.add(startSim, BorderLayout.CENTER);
                simulator.add(stopSim, BorderLayout.LINE_END);
                mtcontrol.add(filler, BorderLayout.PAGE_START);
                mtcontrol.add(startMotor, BorderLayout.LINE_START);
                mtcontrol.add(motorFreq, BorderLayout.CENTER);
                mtcontrol.add(stopMotor, BorderLayout.LINE_END);

                frame.pack();
                frame.setResizable(false);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
        });
    }
    
    static SerialParameters loadSerialParameters() throws Exception {
        SerialParameters sp = new SerialParameters();
        Modbus.setLogLevel(Modbus.LogLevel.LEVEL_DEBUG);
        Properties prop = new Properties();
        File cfg = new File("controller.cfg");
        FileInputStream in = new FileInputStream(cfg);
        prop.load(in);
        in.close();
        String port=prop.getProperty("serial.port");
        int baudRate = Integer.parseInt(prop.getProperty("serial.baudRate", "9600"));
        int dataBits = Integer.parseInt(prop.getProperty("serial.dataBits", "8"));
        int stopBits = Integer.parseInt(prop.getProperty("serial.stopBits", "8"));
        int parity = Integer.parseInt(prop.getProperty("serial.parity", "1"));
        sp.setDevice(port);
        sp.setBaudRate(SerialPort.BaudRate.getBaudRate(baudRate));
        sp.setDataBits(dataBits);
        sp.setParity(SerialPort.Parity.getParity(parity));
        sp.setStopBits(stopBits);
        return sp;
    }
}

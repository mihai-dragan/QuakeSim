import sacformat.SacTimeSeries;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.math.BigDecimal;

public class SimRunner extends Thread {
    ModbusMaster m;
    SacTimeSeries data;
    boolean running = false;
    
    public SimRunner(ModbusMaster m, SacTimeSeries data) {
        this.m = m;
        this.data = data;
    }
    
    public void stopSim(){
        running = false;
    }

    public void run() {
        if(running) return;
        running = true;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        int slaveId = 1;
        try {
            m.writeSingleRegister(slaveId, 99, 1150); // start control in OFF state
            Thread.sleep(80);
            m.writeSingleRegister(slaveId, 100, 800); // set freq% to 800
            Thread.sleep(80);
            m.writeSingleRegister(slaveId, 99, 1151); // control in ON
            Thread.sleep(80);
            int sDelta = new BigDecimal(data.getHeader().getDelta()).multiply(new BigDecimal(1000)).intValue() + 1;
            String startTime = String.format("%04d/%02d/%02d %02d:%02d:%02d.%03d",
                data.getHeader().getNzyear(),
                1,
                data.getHeader().getNzjday(),
                data.getHeader().getNzhour(),
                data.getHeader().getNzmin(),
                data.getHeader().getNzsec(),
                data.getHeader().getNzmsec()
            );
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(startTime));
            int tDelta = 0;
            int tChng = 0;
            boolean goingDown = true;
            int prevY = 0;
            int chngY = 0;
            boolean started = false;
            for (int i=0; i<data.header.getNpts(); i++) {
                tDelta = tDelta + sDelta;
                if(!running) {
                    return;
                }
                if(tDelta>=3000) {
                    if(started || tChng > 3) {
                        System.out.print(sdf.format(new Date(System.currentTimeMillis())) + "\t\t");
                        System.out.printf("%d\n",(tChng*500)/(tDelta/1000));
                        while(true) {
                            try {
                                m.writeSingleRegister(slaveId, 100, (tChng*500)/(tDelta/1000)); // set freq% to ...
                                break;
                            } catch(ModbusIOException miex) {
                                
                            }
                        }
                        Thread.sleep(2960);
                        started = true;
                    }
                    tDelta = 0;
                    tChng = 0;
                }
                
                if(i>0) {
                    float deltaY = prevY - (int)data.y[i];
                    
                    if(deltaY<0 && goingDown) {
                        goingDown = false;
                        if(chngY - prevY > 2000) {
                            chngY = (int)data.y[i];
                            tChng = tChng + 1;
                        }
                    }
                    if(deltaY>0 && !goingDown) {
                        goingDown = true;
                        if(prevY - chngY > 2000) {
                            chngY = (int)data.y[i];
                            tChng = tChng+1;
                        }
                    }
                }
                prevY = (int)data.y[i];
            }
        } catch (InterruptedException ex) {
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            m.writeSingleRegister(slaveId, 100, 800); // set freq% to 0
            Thread.sleep(80);
            m.writeSingleRegister(slaveId, 99, 1150); // start control in OFF state
            Thread.sleep(80);
        }
    }
    
}

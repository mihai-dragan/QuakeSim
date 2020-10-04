/*
 * The TauP Toolkit: Flexible Seismic Travel-Time and Raypath Utilities.
 * Copyright (C) 1998-2000 University of South Carolina This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version. This program
 * is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 59 Temple Place
 * - Suite 330, Boston, MA 02111-1307, USA. The current version can be found at
 * <A HREF="www.seis.sc.edu">http://www.seis.sc.edu </A> Bug reports and
 * comments should be directed to H. Philip Crotwell, crotwell@seis.sc.edu or
 * Tom Owens, owens@seis.sc.edu
 */
// package edu.sc.seis.TauP;
package sacformat;

import static sacformat.SacConstants.FALSE;
import static sacformat.SacConstants.IAMPH;
import static sacformat.SacConstants.IRLIM;
import static sacformat.SacConstants.ITIME;
import static sacformat.SacConstants.IntelByteOrder;
import static sacformat.SacConstants.LITTLE_ENDIAN;
import static sacformat.SacConstants.TRUE;
import static sacformat.SacConstants.data_offset;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.math.BigDecimal;

/**
 * Class that represents a sac file. All headers are have the same names as
 * within the Sac program. Can read the whole file or just the header as well as
 * write a file.
 * 
 * This reflects the sac header as of version 101.4 in utils/sac.h
 * 
 * Notes: Key to comment flags describing each field: Column 1: R required by
 * SAC (blank) optional Column 2: A = settable from a priori knowledge D =
 * available in data F = available in or derivable from SEED fixed data header T
 * = available in SEED header tables (blank) = not directly available from SEED
 * data, header tables, or elsewhere
 * 
 * 
 * @version 1.1 Wed Feb 2 20:40:49 GMT 2000
 * @author H. Philip Crotwell
 */
public class SacTimeSeries {

    public SacTimeSeries() {}

    /** create a new SAC timeseries from the given header and data. The header values
     * related to the data are set correctly:
     * <ul>
     *  <li>npts=data.length</li>
     *  <li>e=b+(npts-1)*delta</li>
     *  <li>iftype=ITIME</li>
     *  <li>leven=TRUE</li>
     * </ul>
     *  Setting of all other headers is the responsibility of the caller.
     * @param header
     * @param data
     */
    public SacTimeSeries(SacHeader header, float[] data) {
        this.header = header;
        header.setIftype(ITIME);
        header.setLeven(TRUE);
        setY(data);
    }

    public SacTimeSeries(File file) throws FileNotFoundException, IOException {
        read(file);
    }

    public SacTimeSeries(String filename) throws FileNotFoundException, IOException {
        this(new File(filename));
    }

    public SacTimeSeries(DataInput inStream) throws IOException {
        read(inStream);
    }

    public float[] getY() {
        return y;
    }

    public void setY(float[] y) {
        this.y = y;
        getHeader().setNpts(y.length);
        if ( ! SacConstants.isUndef(getHeader().getDelta()) && ! SacConstants.isUndef(getHeader().getB())) {
            getHeader().setE(getHeader().getB()+(y.length-1)*getHeader().getDelta());
        }
    }

    public float[] getX() {
        return x;
    }

    public void setX(float[] x) {
        this.x = x;
    }

    public float[] getReal() {
        return real;
    }

    public void setReal(float[] real) {
        this.real = real;
    }

    public float[] getImaginary() {
        return imaginary;
    }

    public void setImaginary(float[] imaginary) {
        this.imaginary = imaginary;
    }

    public float[] getAmp() {
        return amp;
    }

    public void setAmp(float[] amp) {
        this.amp = amp;
    }

    public float[] getPhase() {
        return phase;
    }

    public void setPhase(float[] phase) {
        this.phase = phase;
    }

    public SacHeader getHeader() {
        return header;
    }

    public void printHeader(PrintWriter out) {
        header.printHeader(out);
    }

    public int getNumPtsRead() {
        return numPtsRead;
    }

    public SacHeader header;

    public float[] y;

    private float[] x;

    private float[] real;

    private float[] imaginary;

    private float[] amp;

    private float[] phase;

    public int numPtsRead = 0;

    /**
     * reads the sac file specified by the filename. Only a very simple check is
     * made to be sure the file really is a sac file.
     * 
     * @throws FileNotFoundException
     *             if the file cannot be found
     * @throws IOException
     *             if it isn't a sac file or if it happens :)
     */
    public void read(String filename) throws FileNotFoundException, IOException {
        File sacFile = new File(filename);
        read(sacFile);
    }

    public void read(File sacFile) throws FileNotFoundException, IOException {
        if (sacFile.length() < data_offset) {
            throw new IOException(sacFile.getName() + " does not appear to be a sac file! File size ("
                    + sacFile.length() + " is less than sac's header size (" + data_offset + ")");
        }
        DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(sacFile)));
        try {
            header = new SacHeader(dis);
            if (header.getLeven() == 1 && header.getIftype() == ITIME) {
                if( sacFile.length() != header.getNpts() * 4 + data_offset) {
                    throw new IOException(sacFile.getName() + " does not appear to be a sac file! npts(" + header.getNpts()
                                          + ") * 4 + header(" + data_offset + ") !=  file length=" + sacFile.length() + "\n  as linux: npts("
                                          + SacHeader.swapBytes(header.getNpts()) + ")*4 + header(" + data_offset + ") !=  file length="
                                          + sacFile.length());
                }
            } else if (header.getLeven() == 1 || (header.getIftype() == IAMPH || header.getIftype() == IRLIM)) {
                if( sacFile.length() != header.getNpts() * 4 * 2 + data_offset) {
                    throw new IOException(sacFile.getName() + " does not appear to be a amph or rlim sac file! npts(" + header.getNpts()
                                          + ") * 4 *2 + header(" + data_offset + ") !=  file length=" + sacFile.length() + "\n  as linux: npts("
                                          + SacHeader.swapBytes(header.getNpts()) + ")*4*2 + header(" + data_offset + ") !=  file length="
                                          + sacFile.length());
                }
            } else if (header.getLeven() == 0 && sacFile.length() != header.getNpts() * 4 * 2 + data_offset) {
                throw new IOException(sacFile.getName() + " does not appear to be a uneven sac file! npts("
                                      + header.getNpts() + ") * 4 *2 + header(" + data_offset + ") !=  file length=" + sacFile.length()
                                      + "\n  as linux: npts(" + SacHeader.swapBytes(header.getNpts()) + ")*4*2 + header(" + data_offset
                                      + ") !=  file length=" + sacFile.length());
            }
            readData(dis);
        } finally {
            dis.close();
        }
    }

    public void read(DataInput dis) throws IOException {
        header = new SacHeader(dis);
        readData(dis);
    }

    /** read the data portion of the given File */
    protected void readData(DataInput fis) throws IOException {
        y = new float[header.getNpts()];
        readDataArray(fis, y, header.getByteOrder());
        if (header.getLeven() == FALSE || header.getIftype() == IRLIM || header.getIftype() == IAMPH) {
            x = new float[header.getNpts()];
            readDataArray(fis, x, header.getByteOrder());
            if (header.getIftype() == IRLIM) {
                real = y;
                imaginary = x;
            }
            if (header.getIftype() == IAMPH) {
                amp = y;
                phase = x;
            }
        }
        numPtsRead = header.getNpts();
    }

    /**
     * reads data.length floats. It is up to the caller to insure that the type
     * of SAC file (iftype = LEVEN, IRLIM, IAMPH) and how many data points
     * remain are compatible with the size of the float array to be read.
     * 
     * @throws IOException
     */
    public static void readSomeData(DataInput dataIn, float[] data, boolean byteOrder) throws IOException {
        readDataArray(dataIn, data, byteOrder);
    }

    /**
     * skips samplesToSkip data points. It is up to the caller to insure that
     * the type of SAC file (iftype = LEVEN, IRLIM, IAMPH) and how many data
     * points remain are compatible with the size of the float array to be read.
     * 
     * @throws IOException
     */
    public static int skipSamples(DataInput dataIn, int samplesToSkip) throws IOException {
        return dataIn.skipBytes(samplesToSkip * 4) / 4;
    }

    private static void readDataArray(DataInput fis, float[] d, boolean byteOrder) throws IOException {
        byte[] dataBytes = new byte[d.length * 4];
        int numAdded = 0;
        int i = 0;
        fis.readFully(dataBytes);
        while (numAdded < d.length) {
            if (byteOrder == IntelByteOrder) {
                d[numAdded++] = Float.intBitsToFloat(((dataBytes[i++] & 0xff) << 0) + ((dataBytes[i++] & 0xff) << 8)
                        + ((dataBytes[i++] & 0xff) << 16) + ((dataBytes[i++] & 0xff) << 24));
            } else {
                d[numAdded++] = Float.intBitsToFloat(((dataBytes[i++] & 0xff) << 24) + ((dataBytes[i++] & 0xff) << 16)
                        + ((dataBytes[i++] & 0xff) << 8) + ((dataBytes[i++] & 0xff) << 0));
            }
        }
    }

    /** writes this object out as a sac file. */
    public void write(String filename) throws FileNotFoundException, IOException {
        File f = new File(filename);
        write(f);
    }

    /** writes this object out as a sac file. */
    public void write(File file) throws FileNotFoundException, IOException {
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        try {
            header.writeHeader(dos);
            writeData(dos);
        } finally {
            dos.close();
        }
    }

    public void writeData(DataOutput dos) throws IOException {
        for (int i = 0; i < header.getNpts(); i++) {
            header.writeFloat(dos, y[i]);
        }
        if (header.getLeven() == FALSE || header.getIftype() == IRLIM || header.getIftype() == IAMPH) {
            for (int i = 0; i < header.getNpts(); i++) {
                header.writeFloat(dos, x[i]);
            }
        }
    }

    public static void appendData(File outfile, float[] data) throws IOException {
        RandomAccessFile raFile = new RandomAccessFile(outfile, "rw");
        try {
            SacHeader header = new SacHeader(raFile);
            if (header.getLeven() == FALSE || header.getIftype() == IRLIM || header.getIftype() == IAMPH) {
                raFile.close();
                throw new IOException("Can only append to evenly sampled sac files, ie only Y");
            }
            int origNpts = header.getNpts();
            header.setNpts(header.getNpts() + data.length);
            header.setE((header.getNpts() - 1) * header.getDelta());
            raFile.seek(0);
            header.writeHeader(raFile);
            raFile.skipBytes(origNpts * 4); // four bytes per float
            if (header.getByteOrder() == LITTLE_ENDIAN) {
                // Phil Crotwell's solution:
                // careful here as dos.writeFloat() will collapse all NaN floats
                // to
                // a single NaN value. But we are trying to write out byte
                // swapped values
                // so different floats that are all NaN are different values in
                // the
                // other byte order. Solution is to swap on the integer bits,
                // not the float.
                for (int i = 0; i < data.length; i++) {
                    raFile.writeInt(SacHeader.swapBytes(Float.floatToRawIntBits(data[i])));
                }
            } else {
                for (int i = 0; i < data.length; i++) {
                    raFile.writeFloat(data[i]);
                }
            }
        } finally {
            raFile.close();
        }
    }

    /**
     * just for testing. Reads the filename given as the argument, writes out
     * some header variables and then writes it back out as "outsacfile".
     */
    public static void main(String[] args) throws java.text.ParseException {
        SacTimeSeries data = new SacTimeSeries();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        if (args.length != 1) {
            System.out.println("Usage: java SacTimeSeries sacsourcefile ");
            return;
        }
        try {
            data.read(args[0]);
            //data.getHeader().printHeader();
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
            //System.out.println("stla original: " + data.header.getStla() + " npts=" + data.header.getNpts()+" startTime: "+sdf.format(cal.getTime())+", delta: "+sDelta);
            //if(true) return;
            int tDelta = 0;
            int tChng = 0;
            boolean goingDown = true;
            int prevY = 0;
            int chngY = 0;
            boolean started = false;
            for (int i=0; i<data.header.getNpts(); i++) {
                tDelta = tDelta + sDelta;
                if(tDelta>=3000) {
                    if(started || tChng > 3) {
                        System.out.printf("%.2f|",tChng/(tDelta/1000.0));
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
                //System.out.println(sdf.format(cal.getTime()) +"\t\t"+ data.y[i]);
                //cal.add(Calendar.MILLISECOND,sDelta);
            }
        } catch(FileNotFoundException e) {
            System.out.println("File " + args[0] + " doesn't exist.");
        } catch(IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void fain(String[] args) throws java.text.ParseException {
        SacTimeSeries data = new SacTimeSeries();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        if (args.length != 1) {
            System.out.println("Usage: java SacTimeSeries sacsourcefile ");
            return;
        }
        try {
            data.read(args[0]);
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
            boolean wasAbove = true;
            boolean started = false;
            for (int i=0; i<data.header.getNpts(); i++) {
                tDelta = tDelta + sDelta;
                if(tDelta>=1000) {
                    if(started || tChng > 0) {
                        System.out.printf("%.2f|",tChng/(tDelta/1000.0));
                        started = true;
                    }
                    tDelta = 0;
                    tChng = 0;
                }
                
                if(wasAbove && (int)data.y[i]<2000) {
                    tChng = tChng + 1;
                    wasAbove = false;
                }
                
                if(!wasAbove && (int)data.y[i]>2000) {
                    tChng = tChng + 1;
                    wasAbove = true;
                }
            }
        } catch(FileNotFoundException e) {
            System.out.println("File " + args[0] + " doesn't exist.");
        } catch(IOException e) {
            System.out.println("IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

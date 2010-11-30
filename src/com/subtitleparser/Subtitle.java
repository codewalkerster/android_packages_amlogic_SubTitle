package com.subtitleparser;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.Log;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

import com.subtitleparser.subtypes.*;


/**
 * Various static methods.
 * 
 * @author
 */
public class Subtitle {
	private static final String SUBTITLE_FILE = "/data/subtitle.db";
	private static final String WRITE_SUBTITLE_FILE = "/data/subtitle_img.jpeg";
	private static int subtitle_packet_size = (4+1+4+4+2+2+2+2+2+4+720*576/4);
	public  static int subtitle_file_position = 0;
	private static int sync_byte = 0;
	private static int packet_size = 0;
	private static int read_bytes = 0;
	private static int subtitle_width = 0;
	private static int subtitle_height = 0;
	private static int subtitle_alpha = 0;
	private static int subtitle_pts = 0;
	public  static int subtitle_delay = 0;
	private static Bitmap bf_show = null;
	public enum SUBTYPE
	{	 
		 SUB_INVALID ,
		 SUB_MICRODVD, 
		 SUB_SUBRIP,
		 SUB_SUBVIEWER,
		 SUB_SAMI   ,
		 SUB_VPLAYER ,
		 SUB_RT   ,
		 SUB_SSA  ,
		 SUB_PJS   ,
		 SUB_MPSUB   ,
		 SUB_AQTITLE   ,
		 SUB_SUBVIEWER2 ,
		 SUB_SUBRIP09  ,
		 SUB_JACOSUB  ,
		 SUB_MPL2  ,
		 SUB_DIVX  ,
		 SUB_IDXSUB 
	}
	private String filename=null;
	private String charset=null;
	private SUBTYPE subtype=SUBTYPE.SUB_INVALID;
	private static SubtitleFile subFile=null;
	private static String systemCharset="GBK";
    static {;
    	System.loadLibrary("subjni");
    }
	
	public Subtitle(){	}
	public Subtitle(String name)
	{
		filename=name;
	}
	public void setSubname(String name)
	{
		filename=name;
		charset=null;
		subtype=SUBTYPE.SUB_INVALID;
		subFile=null;
	}
	
	public static void setSystemCharset(String st)
	{
		systemCharset=st;
	}
	
	public String getCharset( )
	{
		if(filename!=null)
		{
			if(charset == null)
			{
				charset=checkEncoding(filename ,systemCharset);
			}
		}
		else
		{
			return null;
		}
		return charset;
	}
	
	public SubtitleFile parse() throws Exception
	{
		if(filename!=null)
		{
			if(charset==null)
			{
				getCharset();
			}
			if(subtype==null)
			{
				getSubType();
			}
			subFile=parseSubtitleFile(filename);
		}
		return subFile;
	}
	
	public SubtitleFile getSubList()
	{
		if(subFile!=null)
			return subFile;
		else 
			return null;
	}
	public SUBTYPE getSubType()
	{
		if(filename!=null)
		{
			if(charset==null)
			{
				getCharset();
			}		
			subtype=Subtitle.fileType(filename,charset);
			return subtype;
		}else
			return null;
	}


    public static native SubtitleFile parseSubtitleFileByJni(String fileName,String encode);



	/**
	 * Parse a known type subtitle file into a SubtitleFile object.
	 * 
	 * @param fileName
	 *            file name;
	 * @return parsed SubtitleFile.
	 */
	public SubtitleFile parseSubtitleFile(String fileName)
			throws Exception {
		SubtitleParser sp = null;
		String input = null;
		
		String encoding=getCharset();
	    SUBTYPE type = getSubType();
	    Log.i("SubtitleFile", "type="+type.toString()+"   encoding="+encoding );

		switch (type) {
//		case TYPE_SRT:
//		    Log.i("SubtitleFile", "------------TYPE_SRT-----------"+fileName );
//			input = FileIO.file2string(fileName, encoding);
//			sp = new SrtParser();
//			break;
//		case TYPE_SUB:
//			input = FileIO.file2string(fileName, encoding);
//			sp = new SubParser();
//			break;
		case SUB_SSA:	
			input = FileIO.file2string(fileName, encoding);
			sp=new SsaParser();
			return sp.parse(input);
		case SUB_SAMI:
//			input = FileIO.file2string(fileName, encoding);
//			sp=new SamiParser(); 
//			return sp.parse(input);
		case SUB_MICRODVD:
		case SUB_SUBRIP:
		case SUB_SUBVIEWER:
		case SUB_VPLAYER :
		case SUB_RT   :
		case SUB_PJS   :
		case SUB_MPSUB   :
		case SUB_AQTITLE   :
		case SUB_SUBVIEWER2 :
		case SUB_SUBRIP09  :
		case SUB_JACOSUB  :
		case SUB_MPL2  :
		case SUB_DIVX:
			Log.i("SubtitleFile", "------------parseSubtitleFileByJni-----------"+fileName );
			sp=new TextSubParser(); 
		    return sp.parse(fileName,encoding);
		default:
			sp = null;
			throw new Exception("Unknown File Extension.");
		}
	}



	/**
	 * @return the file type analyzing only the extension. 
	 */
	public static SUBTYPE fileType(String file,String encoding) {
		if (file.endsWith(".idx"))
			return SUBTYPE.SUB_IDXSUB;	
		return FileIO.dectFileType(file,encoding);
	}
	
	// 2-byte number
	private static int SHORT_little_endian_TO_big_endian(int i)
	{
	    return ((i>>8)&0xff)+((i << 8)&0xff00);
	}

	// 4-byte number
	private static int INT_little_endian_TO_big_endian(int i)
	{
	    return((i&0xff)<<24)+((i&0xff00)<<8)+((i&0xff0000)>>8)+((i>>24)&0xff);
	}
	public static Bitmap getBitmap(int tick)  {
		//int buffer[]={12,12,12,123,45,42,342,4,25,235,345,34,53,45,345,34};
		//return Bitmap.createBitmap( buffer,4,4, Bitmap.Config.ALPHA_8  );
		try {
			File f=new File(SUBTITLE_FILE); 
			RandomAccessFile raf=new RandomAccessFile(f,"r");
			//seek to subtitle packet
			raf.seek(subtitle_file_position*subtitle_packet_size);
			FileChannel fc = raf.getChannel();
			
			
			sync_byte = raf.readInt();
			//Log.i(LOG_TAG,"Subtitle file first four bytes are: " + sync_byte);
			sync_byte = INT_little_endian_TO_big_endian(sync_byte);
			//Log.i(LOG_TAG,"Subtitle file first four bytes reverse are: " + sync_byte);
			raf.skipBytes(1);
			subtitle_pts = INT_little_endian_TO_big_endian(raf.readInt());
			//Log.i(LOG_TAG,"millisec is : " + millisec);
			//Log.i(LOG_TAG,"subtitle_pts is : " + subtitle_pts);
			if(tick*90 < subtitle_pts)
				return null;
			subtitle_delay = INT_little_endian_TO_big_endian(raf.readInt());

			
			raf.skipBytes(4);
			subtitle_width = SHORT_little_endian_TO_big_endian(raf.readShort());
			subtitle_height = SHORT_little_endian_TO_big_endian(raf.readShort());
			subtitle_alpha = SHORT_little_endian_TO_big_endian(raf.readShort());
			int RGBA_Pal[] = new int[4];
			RGBA_Pal[0] = RGBA_Pal[1] = RGBA_Pal[2] = RGBA_Pal[3] = 0;
			if((subtitle_alpha&0xff0)!=0)
            {
                RGBA_Pal[2] = 0xffffffff;
				RGBA_Pal[1] = 0xff; 
            }
            else if((subtitle_alpha&0xfff0)!=0){
                RGBA_Pal[1] = 0xff;
				RGBA_Pal[2] = 0xffffffff; 
				RGBA_Pal[3] = 0xff;
            }
            else if((subtitle_alpha&0xf0f0)!=0){
                RGBA_Pal[1] = 0xffffffff;
				RGBA_Pal[3] = 0xff;
            }
            else if((subtitle_alpha&0xff00)!=0){
				RGBA_Pal[2] = 0xffffffff; 
				RGBA_Pal[3] = 0xff;
            }
			else{
				RGBA_Pal[1] = 0xffffffff;
				RGBA_Pal[3] = 0xff;
			}
			packet_size = raf.readInt();
			//Log.i(LOG_TAG,	"packet size is : " + packet_size);
			packet_size = INT_little_endian_TO_big_endian(packet_size);
			//Log.i(LOG_TAG,	"packet size reverse is : " + packet_size);
			//allocate equal size buffer
			ByteBuffer bf = ByteBuffer.allocate(packet_size);
			read_bytes = fc.read(bf, subtitle_file_position*subtitle_packet_size+23);
			//Log.i(LOG_TAG,	"read subtitle packet size is : " + read_bytes);
			
			int i=0,j=0,n=0,index=0,index1=0,data_byte=0,buffer_width=0;
			buffer_width = (subtitle_width+63)&0xffffffc0;
			data_byte = (((subtitle_width*2)+15)>>4)<<1;
			bf_show = Bitmap.createBitmap(buffer_width,subtitle_height, Config.ARGB_8888);
			//ByteBuffer bf_show = ByteBuffer.allocate(buffer_width*subtitle_height*4);
			byte[] data = new byte[200];
			//Log.i(LOG_TAG,	"subtitle_width  is : " + subtitle_width);
			//Log.i(LOG_TAG,	"subtitle_height  is : " + subtitle_height);
			//Log.i(LOG_TAG,	"biffer_width  is : " + buffer_width);
			//Log.i(LOG_TAG,	"data_byte  is : " + data_byte);

			for (i=0;i<subtitle_height;i++){
    			if((i&1)!=0){
    				bf.position((i>>1)*data_byte + (720*576/8));
    				bf.get(data, 0, data_byte);
    			}
    			else{
    				bf.position((i>>1)*data_byte);
    				bf.get(data, 0, data_byte);
    			}
    			index=0;
    			for (j=0;j<subtitle_width;j++){

    				index1 = ((index%2)>0)?(index-1):(index+1);
    				n = data[index1];
    				index++;
    				if(n!=0){    					
    					bf_show.setPixel(j, i, RGBA_Pal[(n>>4)&0x3]);    					
	                    //result_buf[i*(buffer_width)+j] = RGBA_Pal[(n>>4)&0x3];
	                    if(++j >= subtitle_width)    break;
	                    bf_show.setPixel(j, i, RGBA_Pal[(n>>6)&0x3]);
	                    //result_buf[i*(buffer_width)+j] = RGBA_Pal[n>>6];
	                    if(++j >= subtitle_width)    break;
	                    bf_show.setPixel(j, i, RGBA_Pal[n&0x3]);	                    
	                    //result_buf[i*(buffer_width)+j] = RGBA_Pal[n&0x3];
	                    if(++j >= subtitle_width)    break;	 
	                    bf_show.setPixel(j, i, RGBA_Pal[(n>>2)&0x3]);      
	                    //result_buf[i*(buffer_width)+j] = RGBA_Pal[(n>>2)&0x3];
    				}
    				else
    					j+=3;
				}
			}

			raf.close();
			
			subtitle_file_position++;
			if(subtitle_file_position >= 100)
				subtitle_file_position = 0;

			//Log.i(LOG_TAG,	"end draw bitmap ");

			//invalidate();
			return bf_show;
			
		}
		catch (FileNotFoundException ex) {
			ex.printStackTrace();
		}
		catch (IllegalArgumentException ex){
			ex.printStackTrace();
		}
		catch (IOException ex){
			ex.printStackTrace();
		}
		return null;
	}

	private static String checkEncoding(String fileName, String enc)
	{
		BufferedInputStream bis = null;
		byte[] first3Bytes=new byte[3];
		String charset=enc;

		try {
			bis = new BufferedInputStream(new FileInputStream(new File(fileName)));
			bis.mark(0);
			int r =bis.read( first3Bytes, 0, 3) ;
			if(r == -1)
			{
				return charset;
			}
				
			if (first3Bytes[0] == (byte) 0xFF && first3Bytes[1] == (byte) 0xFE)
	        {
				charset = "UTF-16LE";
	        }
            else if (first3Bytes[0] == (byte) 0xFE
                    && first3Bytes[1] == (byte) 0xFF)
            {
            	charset = "UTF-16BE";
            }
            else if (first3Bytes[0] == (byte) 0xEF
                    && first3Bytes[1] == (byte) 0xBB
                    && first3Bytes[2] == (byte) 0xBF)
            {
            	charset="UTF8";
            }else
            {
            	if(1>0)//whether need to probe the file charset
            	{
            		String probe=probe_utf8_utf16(fileName,1024);
            		if(probe!="")
            		{
            			charset=probe;
            		}
            	}
            }

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    Log.i("checkEncoding", "--filename:--"+fileName+"-charset:"+charset );
		return charset;
	}

	
	private static String probe_utf8_utf16(String fileName,int size)
	{
		char probe_find_time=10;
		int utf8_count = 0;
		int little_utf16 = 0;
		int big_utf16 = 0;
		int i=0;
		byte[] probeBytes=new byte[size];
		BufferedInputStream bis = null;
		int r=0;
		try {
			bis = new BufferedInputStream(new FileInputStream(new File(fileName)));
			bis.mark(0);
			r = bis.read( probeBytes, 0, size);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(r == -1)
		{
			return "";
		}
		for(i=0; i<(size-5); i++){
			if(probeBytes[i] == 0 && (probeBytes[i+1]>0x20 && probeBytes[i+1]<0x7d)){
				i++;
				big_utf16++;
			}
			else if(probeBytes[i+1] == 0 && (probeBytes[i]>0x20 && probeBytes[i]<0x7d)){
				i++;
				little_utf16++;
			}
			else if(((probeBytes[i]&0xE0)==0xE0)&&((probeBytes[i+1]&0xC0)==0x80)&&((probeBytes[i+2]&0xC0)==0x80)){
				i+=2;
				utf8_count++;
				if((((probeBytes[i+1]&0x80)==0x80) && ((probeBytes[i+1]&0xE0) != 0xE0))|| 
					(((probeBytes[i+2]&0x80)==0x80) && ((probeBytes[i+2]&0xC0) != 0x80))|| 
					(((probeBytes[i+3]&0x80)==0x80) && ((probeBytes[i+3]&0xC0) != 0x80)))
					return "";
			}
			if(big_utf16 >= probe_find_time)
				return "UTF-16BE";
			else if(little_utf16 >= probe_find_time)
				return "UTF-16LE";
			else if(utf8_count >=probe_find_time )
				return "UTF-8";
		}
		if(i == (size - 2)){
			if(big_utf16 > 0)
				return "UTF-16BE";
			else if(little_utf16 > 0)
				return "UTF-16LE";
			else if(utf8_count > 0)
				return "UTF-8";
		}
		return "";
	}

	/**
	 * Simple integer formatter.
	 * 
	 * @param n
	 *            integer to format;
	 * @param chars
	 *            number of char on which represent n;
	 * @return n on chars characters.
	 */
	public static String format(int n, int chars) {

		NumberFormat numberFormatter;

		numberFormatter = NumberFormat.getNumberInstance();
		numberFormatter.setMinimumIntegerDigits(chars);
		numberFormatter.setMaximumIntegerDigits(chars);

		return numberFormatter.format(n);
	}

	/**
	 * Parse shift time.
	 * 
	 * @param shift
	 *            String that contains a representation of the shift in seconds
	 *            (e.g. "12","13.5");
	 * @return milliseconds
	 */
	public static int parseShiftTime(String shift) throws NumberFormatException {

		String tmp[] = shift.split("\\.");

		if (tmp.length == 1) {
			return Integer.parseInt(tmp[0]) * 1000;
		}
		if (tmp.length == 2) {
			int dec = Integer.parseInt(tmp[1]);

			if ((dec > 999) || (dec < 0)) {
				throw new NumberFormatException("Unpermitted shift value.");
			}
			if ((dec > 0) && (dec <= 9)) {
				dec = dec * 100;
			} // 1-9
			else {
				if ((dec >= 10) && (dec <= 99)) {
					dec = dec * 10;
				}// 10-99
			}
			if (Integer.parseInt(tmp[0]) < 0) {
				return Integer.parseInt(tmp[0]) * 1000 - dec;
			} else {
				return Integer.parseInt(tmp[0]) * 1000 + dec;
			}
		}
		throw new NumberFormatException("Unpermitted shift value.");
	}

	/**
	 * Remove hearing impaired subtitles.
	 * 
	 * @param text
	 *            with hearing impaired subtitles'
	 * @param start
	 *            char (e.g. '[');
	 * @param end
	 *            char (e.g. ']');
	 * @return text without Hearing Impaired Subtitles
	 */
	public static String removeHearImp(String text, String start, String end) {

		String res = text;
		Pattern p = Pattern.compile("\\" + start + ".*?" + "\\" + end);
		Matcher m = p.matcher(res);
		while (m.find()) {
			res = res.substring(0, m.start())
					+ res.substring(m.end(), res.length());
			m = p.matcher(res);
		}
		return res;
	}

	/**
	 * Frame/MilliSec Converter.
	 * 
	 * @param frames
	 *            n of frames
	 * @param framerate
	 *            framerate (frames per sec)
	 * @return milliseconds
	 */
	public static int frame2mil(int frames, float framerate) throws Exception {
		if (framerate <= 0)
			throw new Exception(
					"frame2mil I need a positive framerate to perform this conversion!");

		Float fl = new Float(frames / framerate * 1000);
		return fl.intValue();
	}

	/**
	 * MilliSec/Frame Converter.
	 * 
	 * @param millisec
	 *            n of millisec
	 * @param framerate
	 *            framerate (frames per sec)
	 * @return frames
	 */
	public static int mil2frame(int millisec, float framerate) throws Exception {
		if (framerate <= 0)
			throw new Exception(
					" mil2frame I need a positive framerate to perform this conversion!");

		millisec = millisec / 1000;
		Float fl = new Float(millisec * framerate);
		return fl.intValue();
	}

	// Makes a non-format specific SubtitleFile
	public static SubtitleFile fillValues(SubtitleFile sf, float framerate)
			throws Exception {

		for (Object x : sf) {
			((SubtitleLine) x).getBegin().setAllValues(framerate);
			((SubtitleLine) x).getEnd().setAllValues(framerate);
		}

		return sf;
	}
}

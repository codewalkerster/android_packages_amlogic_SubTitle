package com.subtitleparser.subtypes;
import java.util.Arrays;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.util.Log;

import com.subtitleparser.MalformedSubException;
import com.subtitleparser.SubData;
import com.subtitleparser.SubtitleApi;
import com.subtitleparser.SubtitleFile;
import com.subtitleparser.SubtitleLine;
import com.subtitleparser.SubtitleParser;

class IdxSubApi extends SubtitleApi
{
	native static RawData getIdxsubRawdata(int millisec); 
	native void setIdxFile(String name);
	 private Bitmap bitmap=null;
	 private String filename;
	 IdxSubApi(String name)
	 {
		 filename =name;
		 setIdxFile(filename);
	 };

	 public SubData getdata(int millisec )
	 {
		 //add  value to bitmap
		 //add  value to begingtime,endtime
		 	getIdxsubRawdata(millisec);
		    if(millisec%8000>4000)
		    {
			    Log.i("InSubApi", "------------getdata1-----------" );
				int[] data = new int[100000];
				Arrays.fill(data, 0x55555500);
				bitmap= Bitmap.createBitmap( data,  50,  250, Config.ARGB_8888  ) ;
		    }else
		    {
			    Log.i("InSubApi", "------------getdata2-----------" );
				int[] data = new int[100000];
				Arrays.fill(data, 0x99999900);    	
				bitmap= Bitmap.createBitmap( data,  150,  180, Config.ARGB_8888  ) ;
		    }
			return new SubData( bitmap, millisec,millisec+3);
	 };
}


public class IdxSubParser implements SubtitleParser{
	
	public SubtitleApi parse(String filename) throws MalformedSubException{
		//call jni to init parse;
		return new IdxSubApi(filename);
	};

	public SubtitleApi parse(String inputString,String st2) throws MalformedSubException{
		return null;
	};
}
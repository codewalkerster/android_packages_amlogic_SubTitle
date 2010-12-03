package com.subtitleparser.subtypes;
import android.graphics.Bitmap;

import com.subtitleparser.MalformedSubException;
import com.subtitleparser.SubData;
import com.subtitleparser.SubtitleApi;
import com.subtitleparser.SubtitleFile;
import com.subtitleparser.SubtitleLine;
import com.subtitleparser.SubtitleParser;

class IdxSubApi extends SubtitleApi
{
	 private Bitmap bitmap=null;
	 IdxSubApi(){};

	 public SubData getdata(int millisec )
	 {
		 //add  value to bitmap
		 //add  value to begingtime,endtime

		
		return new SubData( bitmap, begingtime,endtime);
	 };
}


public class IdxSubParser implements SubtitleParser{
	
	public SubtitleApi parse(String filename) throws MalformedSubException{
		//call jni to init parse;
		return new IdxSubApi();
	};

	public SubtitleApi parse(String inputString,String st2) throws MalformedSubException{
		return null;
	};
}
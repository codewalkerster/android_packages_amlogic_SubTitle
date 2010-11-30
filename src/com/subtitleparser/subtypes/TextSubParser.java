package com.subtitleparser.subtypes;

import android.util.Log;

import com.subtitleparser.MalformedSubException;
import com.subtitleparser.SubtitleFile;
import com.subtitleparser.SubtitleParser;
import com.subtitleparser.Subtitle;


/**
* a .SRT subtitle parser.
*
* @author
*/
public class TextSubParser implements SubtitleParser {

	public SubtitleFile parse(String filename,String encode) throws MalformedSubException{

		SubtitleFile file=Subtitle.parseSubtitleFileByJni(filename, encode);
		if(file==null)
		{
		    Log.i("TextSubParser", "------------err-----------" );

			throw new MalformedSubException("text sub parser return NULL!");
		}else
		{	
			return file;
		}
	};
	public SubtitleFile parse(String inputstring) throws MalformedSubException{
		return null;
};

}

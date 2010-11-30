package com.subtitleparser.subtypes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

import com.subtitleparser.MalformedSubException;
import com.subtitleparser.SubtitleFile;
import com.subtitleparser.SubtitleLine;
import com.subtitleparser.SubtitleParser;
import com.subtitleparser.SubtitleTime;

/**
* a .SUB subtitle parser.
*
* @author jeff.yang
*/
public class SsaParser implements SubtitleParser{
	
	public SubtitleFile parse(String inputString) throws MalformedSubException{
		try{
			String n="\\"+System.getProperty("line.separator");
			String tmpText="";
			SubtitleFile sf=new SubtitleFile();
			SubtitleLine sl=null;
			
			//SSA regexp
			Pattern p = Pattern.compile(
					"Dialogue:[^,]*,\\s*"+"(\\d):(\\d\\d):(\\d\\d).(\\d\\d)\\s*,\\s*"
					+"(\\d):(\\d\\d):(\\d\\d).(\\d\\d)"+
					"[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,"+
					"(.*?)"+n
			);

			Matcher m = p.matcher(inputString);

			int occ=0;
			while(m.find()){

				occ++;
				sl=new SubtitleLine(occ,
						new SubtitleTime(Integer.parseInt(m.group(1)), //start time
								Integer.parseInt(m.group(2)),
								Integer.parseInt(m.group(3)),
								Integer.parseInt(m.group(4))),
						new SubtitleTime(Integer.parseInt(m.group(5)), //end time
								Integer.parseInt(m.group(6)),
								Integer.parseInt(m.group(7)),
								Integer.parseInt(m.group(8))),
				m.group(9) //text
				);
				tmpText="";
				sf.add(sl);
			}
			Log.i("SsaParser", "find"+sf.size());
			return sf;
		}catch(Exception e)
		{
			Log.i("SsaParser", "------------!!!!!!!parse file err!!!!!!!!");
		    throw new MalformedSubException(e.toString());
		}
	};
	public SubtitleFile parse(String inputString,String st2) throws MalformedSubException{
		return null;
	};
	
}
package com.subtitleparser;



/**
 * a subtitle parser.
 *
 * @author 
 */
public interface SubtitleParser{
	
	public SubtitleFile parse(String s ) throws MalformedSubException;
	public SubtitleFile parse(String s1,String s2 ) throws MalformedSubException ;

	//add support pixmap: get w, h,source,and then change to Bitmap 
	//public Bitmap curBitmap();
}

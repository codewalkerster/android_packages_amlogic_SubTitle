package com.subtitleparser.subtypes;

public class RawData
{
	public RawData(int[] data,int t,int w,int h,int delay,String st)// ([BILjava/lang/String;)V
	{
		rawdata=data;type=t;width=w;height=h;sub_delay=delay;codec=st;
	}
	int[] rawdata;
	int type;
	int width;
	int height;
	int sub_delay;    //ms
	String codec;
}
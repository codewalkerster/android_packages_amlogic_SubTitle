package com.subtitleparser.subtypes;

public class RawData
{
	RawData(int[] data,int t,int w,int h,String st)// ([BILjava/lang/String;)V
	{
		rawdata=data;type=t;width=w;height=h;codec=st;
	}
	int[] rawdata;
	int type;
	int width;
	int height;
	String codec;
}
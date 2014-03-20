package com.amlogic.SubTitleService;

interface ISubTitleService
{
	void open(String path);
	void openIdx(int idx);
	void close();
	int getSubTotal();
	void nextSub();
	void preSub();
	void startInSubThread();
	void stopInSubThread();
	void showSub(int position);
	void option(); 
	void setTextColor(int color);
	void setTextSize(int size);
	void setGravity(int gravity);
	void setTextStyle(int style);
	void setPosHeight(int height);
	void clear();
	void hide();
	void display();
	String getCurName();
}
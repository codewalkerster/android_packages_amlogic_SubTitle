package com.subtitleview;

import android.R.bool;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;
import com.subtitleparser.*;
import android.graphics.Matrix;
import android.graphics.Canvas;

public class SubtitleView extends TextView {
	private static final String LOG_TAG = SubtitleView.class.getSimpleName();
	private SubtitleApi subapi = null;
	private boolean needSubTitleShow= true;
	private Subtitle.SUBTYPE type=Subtitle.SUBTYPE.SUB_INVALID;
	private boolean InsubStatus=false;
	private Bitmap inter_bitmap = null;
	private Subtitle subtitle=null;
	private int timeoffset=1000;
	
//	public void setInsubStatus(boolean flag)
//	{
//		InsubStatus=flag;
//		
//		if(InsubStatus)
//		{
//			setText("");
//			subapi = null;
//		}
//	}
	

	public SubtitleView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public SubtitleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		subtitle = new Subtitle();
	}

	public SubtitleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		subtitle = new Subtitle();
	}
	public void setViewStatus(boolean flag )
	{
		needSubTitleShow=flag;
		if(flag==false)
		{
			setVisibility( INVISIBLE); 
		}
		else
		{
			setVisibility( VISIBLE); 
		}
	}
	public void tick(int millisec) {

		if (needSubTitleShow==false) {
			return;
		}
		if (subapi == null)
		{
			Log.i(LOG_TAG,	"subapi!!!!!!!!!!!!!!!  null"  );
			return;
		}
			
		SubData data = subapi.getdata(millisec+1000);
		if(data == null)
		{
			Log.i(LOG_TAG,	"SubData return  null"  );
		}
		else
		{
			if(data.gettype()==1)
			{
				Log.i(LOG_TAG,"start gettype()==1 bitmap");		
				inter_bitmap=data.getSubBitmap();
				if(inter_bitmap!=null)
				{
					Log.i(LOG_TAG,	"window" +this.getWidth()+"X"+this.getHeight() );
					Log.i(LOG_TAG,	"invalidate " +inter_bitmap.getWidth()+"X"+inter_bitmap.getHeight() );
			        this.setMinimumWidth(inter_bitmap.getWidth());
			        this.setMinimumHeight(inter_bitmap.getHeight());
					Log.i(LOG_TAG,	"window" +this.getWidth()+"X"+this.getHeight() );
			        invalidate(); 
				}
				return;
			}else
			{
				setText(data.getSubString());
		    }
		}
	}
	
	public void setDelay(int milsec) 
	{
		timeoffset=milsec;
	}
	
	@Override
	public void onDraw(Canvas canvas) 
	{
		 /*
		 if(mbmpTest!=null)
		 {
		     Rect rtSource = new Rect(0,0,320,240);
		     Rect rtDst = new Rect(0,0,320,240);
		     canvas.drawBitmap(mbmpTest, rtSource,rtDst, mPaint);
		 }
		*/
		
		 if(inter_bitmap!=null)
		 {
		   Matrix matrix = new Matrix();
           matrix.postScale(1.0f, 1.0f);
           //matrix.setRotate(90,120,120);
           Log.i("SubView", "----"+inter_bitmap.getWidth()+inter_bitmap.getHeight() );

           canvas.drawBitmap(inter_bitmap, 0, 0, null);
           Log.i(LOG_TAG,
			"end draw bitmap ");
           //inter_bitmap.recycle();
           inter_bitmap = null;
		 } 	
		 super.onDraw(canvas); 

    }

	public Subtitle.SUBTYPE setFile(String file, String enc) throws Exception {
		subapi = null;
		InsubStatus=false;
		// load Input File
		try {
		    Log.i("SubView", "------------setFile-----------" +file);
			subtitle.setSubname(file);
		    type=subtitle.getSubType();
		    if (type==Subtitle.SUBTYPE.SUB_INVALID) 
		    {
		    	subapi =null;
		    }
		    else
		    {
		    	
		    	subapi =subtitle.parse();
	    	}
		Log.i("SubView", "--subapi=----------------" +subapi);
		} catch (Exception e) {
		    Log.i("SubView", "------------err-----------" );
			throw e;
		}

		return type;
	}

	public SubtitleApi getSubtitleFile() {
		return subapi;
	}

//	public void reSet() {
//		setText("");
//		setCompoundDrawablesWithIntrinsicBounds(null, null, null,null); 
//		if (subapi != null) {
//			subapi.setCurSubtitleIndex(0);
//		}
//	}
//
//	public void showPrevSubtitle() {
//		if (subFile == null||InsubStatus==false) {
//			return;
//		}
//
//		subFile.toPrevSubtitle();
//
//		setText(subFile.curSubtitle().getText());
//	}
//
//	public void showNextSubtitle() {
//		if (subFile == null||InsubStatus==false) {
//			return;
//		}
//
//		subFile.toNextSubtitle();
//
//		setText(subFile.curSubtitle().getText());
//	}
}

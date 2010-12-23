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
import android.graphics.Rect;
import android.widget.LinearLayout;

public class SubtitleView extends TextView {
	private static final String LOG_TAG = SubtitleView.class.getSimpleName();
	private SubtitleApi subapi = null;
	private boolean needSubTitleShow= true;
	private Subtitle.SUBTYPE type=Subtitle.SUBTYPE.SUB_INVALID;
	private boolean InsubStatus=false;
	private Bitmap inter_bitmap = null;
	private Subtitle subtitle=null;
	private int timeoffset=1000;
	private SubData data =null;
	private int graphicViewMode = 0;
	private boolean hasopenInsubfile = false;
	public void setGraphicSubViewMode(int flag)
	{
		graphicViewMode=flag;
	}
	

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
		int modifytime =millisec+1000;
		if(data!=null)
		{
			Log.i(LOG_TAG,	"modifytime :"+modifytime  +"exist b:"+data.beginTime()+" e:"+data.endTime() );

			if(modifytime>=data.beginTime()&& modifytime<=data.endTime())
			{
				return ;
			}
		}	
			
		data = subapi.getdata(modifytime);
		if(data == null)
		{
			Log.i(LOG_TAG,	"SubData return  null,so clear the view content"  );
			setText("");
			inter_bitmap=null;
			invalidate(); 
		}
		else
		{
			if(data.gettype()==1)
			{
				Log.i(LOG_TAG,"start gettype()==1 bitmap");		
				inter_bitmap=data.getSubBitmap();
				if(inter_bitmap!=null)
				{
//					Log.i(LOG_TAG,	"window" +this.getWidth()+"X"+this.getHeight() );
//					Log.i(LOG_TAG,	"invalidate " +inter_bitmap.getWidth()+"X"+inter_bitmap.getHeight() );
					//setLayoutParams(new LinearLayout.LayoutParams(inter_bitmap.getWidth(),inter_bitmap.getHeight()));
//					Log.i(LOG_TAG,	"window" +this.getWidth()+"X"+this.getHeight() );
			        invalidate(); 
				}
				return;
			}else
			{
//				Log.i(LOG_TAG,	"window............." +this.getWidth()+"X"+this.getHeight() );
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
           int offset_x =0;
           int offset_y =0;
           int display_width=0;
           int display_height=0;
           
           
//           Log.i(LOG_TAG,"...window.......bitmap......." +this.getWidth()+"X"+this.getHeight() +"  " +inter_bitmap.getWidth()+"X"+inter_bitmap.getHeight() );
   	   	  if(graphicViewMode==0)
   	   	  {  
   	   		  if(inter_bitmap.getHeight()<=this.getHeight()&&inter_bitmap.getWidth()<=this.getWidth())
   	   		  {
  				 display_width = inter_bitmap.getWidth();
	       	     display_height =inter_bitmap.getHeight();
	       	     offset_x = (this.getWidth()-display_width)/2;
	       	     offset_y = this.getHeight()-display_height;  
   	   		  }else
   	   		  {
   				if((((float)this.getWidth())/inter_bitmap.getWidth())>(((float)this.getHeight())/inter_bitmap.getHeight()))
   				{
   					display_width = inter_bitmap.getWidth()*this.getHeight()/inter_bitmap.getHeight();
   		       	    display_height = this.getHeight();		
   		       	    offset_x = (this.getWidth()-display_width)/2;
   		       	    offset_y = 0;
   				}else 
   				{
   					display_width = this.getWidth();
   		       	    display_height =inter_bitmap.getHeight()*this.getWidth()/inter_bitmap.getWidth();
   		       	    offset_x = 0;
   		       	    offset_y = this.getHeight()-display_height;        	    		
   				}		  
   	   		  }
   	   	  }else
   	   	  {
			if((((float)this.getWidth())/inter_bitmap.getWidth())>(((float)this.getHeight())/inter_bitmap.getHeight()))
			{
				display_width = inter_bitmap.getWidth()*this.getHeight()/inter_bitmap.getHeight();
	       	    display_height = this.getHeight();		
	       	    offset_x = (this.getWidth()-display_width)/2;
	       	    offset_y = 0;
			}else 
			{
				display_width = this.getWidth();
	       	    display_height =inter_bitmap.getHeight()*this.getWidth()/inter_bitmap.getWidth();
	       	    offset_x = 0;
	       	    offset_y = this.getHeight()-display_height;        	    		
			}
   	   	  }
			
//test mode 
//   	   	  {
//				display_width = inter_bitmap.getWidth();
//	       	    display_height =inter_bitmap.getHeight();
//	       	    offset_x = (this.getWidth()-display_width)/2;
//	       	    offset_y = this.getHeight()-display_height;    
//   	   	  }
			
//			Log.i(LOG_TAG, "....x y w h.........."+offset_x+" "+offset_y+" "+display_width+" "+display_height );

           
           Rect Src = new Rect(0,0,inter_bitmap.getWidth(),inter_bitmap.getHeight());
		   Rect Dst = new Rect(offset_x,offset_y,offset_x+display_width,offset_y+display_height);
           canvas.drawBitmap(inter_bitmap,Src,Dst,null);
   

           
//           Log.i(LOG_TAG,"end draw bitmap ");
           //inter_bitmap.recycle();
           inter_bitmap = null;
		 } 	
		 super.onDraw(canvas); 

    }
    
    public void closeSubtitle()
    {
		if(subapi!=null)
		{
			Log.i("SubView", "------------release subtitle-----------" );
			subapi.closeSubtitle();
			subapi = null;				
		}    	
		if(hasopenInsubfile==true)
		{
			hasopenInsubfile=false;
			subtitle.setSubname("INSUB"); 
			try {
				subapi=subtitle.parse();
				subapi.closeSubtitle();
				subapi=null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    }

	public Subtitle.SUBTYPE setFile(SubID file, String enc) throws Exception {

		if(subapi!=null)
		{
			if(subapi.type()==Subtitle.SUBTYPE.INSUB)
			{
				//don't release now,apk will call closeSubtitle when change file.
			}else
			{
				subapi.closeSubtitle();
				subapi = null;
			}
		}
		subtitle.setSystemCharset("BIG5");
		InsubStatus=false;
		// load Input File
		try {
		    Log.i("SubView", "------------setFile-----------" +file.filename);
			subtitle.setSubID(file);
		    type=subtitle.getSubType();
		    if (type==Subtitle.SUBTYPE.SUB_INVALID) 
		    {
		    	subapi =null;
		    }
		    else
		    {
		    	if(type==Subtitle.SUBTYPE.INSUB)
		    	{
		    		hasopenInsubfile =true;
		    	}
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

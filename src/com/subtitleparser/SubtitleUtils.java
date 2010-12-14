package com.subtitleparser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class SubtitleUtils {
	public native int getInSubtitleTotalByJni();
	
	public native int setInSubtitleNumberByJni(int  ms);
	public native int getCurrentInSubtitleIndexByJni();
	public native void FileChangedByJni(String name);

	private String filename=null;
	private File subfile =null;
	private List<String> strlist = new ArrayList<String>();
	private int exSubtotle=0;
	
    private static final String[] extensions = {
    	"txt",
        "srt",
        "smi",
        "sami",
        "rt",
        "ssa",
        "ass",
        "idx",
        "sub",
       /* "may be need add new types--------------" */};	
	public  SubtitleUtils() {
	}
    
	public  SubtitleUtils(String name) {
		setFileName(name);
	}
	
	public void setFileName(String name) {
		if(filename !=name)
		{
			strlist.clear();
			filename = name;
			FileChangedByJni(name);
			subfile= new File(filename);
			accountExSubtitleNumber();
		}
	}
	
    public int getSubTotal()
    {
    	return exSubtotle+accountInSubtitleNumber();
    }
   
	public int getInSubTotal()
    {
    	return accountInSubtitleNumber();
    }
    public String getSubPath(int index)
    {
    	if(subfile==null)
    		return null ;
    	
    	if(index<exSubtotle)
    		return strlist.get(index);
    	else if(index<getSubTotal())
    	{
    		setInSubtitleNumber(index-exSubtotle);
    		return "INSUB";
    	}
		return null;

    }
    
    private void  accountExSubtitleNumber()
    {
    	String tmp=subfile.getName();
    	String prefix=tmp.substring(0, tmp.lastIndexOf('.')+1);
		Log.i("SubtitleUtils",	""+ prefix   );

      	File DirFile= subfile.getParentFile();
    	if(DirFile.isDirectory())
    	{
	    	for (String file : DirFile.list()) 
	    	{
	    		if(file.startsWith(prefix))
	    		{
	    	        for (String ext : extensions) {
	    	            if (file.toLowerCase().endsWith(ext))
	    	            {
	    	            	strlist.add(DirFile.getAbsolutePath()+"/"+file);
	    	            	break;
	    	            }
	    	        }	    			
	    		}
	    	}
	    	for(String file : strlist)
	    	{
	    		if(file.toLowerCase().endsWith("idx"))
	    		{
	    			Log.v("before: ",""+file );
	    			String st=file.substring(0, file.length()-3);
	    			for(int i=0;i<strlist.size();i++)
	    			{
	    				if(strlist.get(i).toLowerCase().endsWith("sub")&&
	    						strlist.get(i).startsWith(st)&&
	    						strlist.get(i).length()==file.length())
	    				{
	    	    			Log.v("accountExSubtitleNumber: ","clear "+strlist.get(i) );
	    					strlist.remove(i);
	    				}
	    			}
	    		}
	    	}
    	}
    	exSubtotle=strlist.size();
    }
    
    //wait to finish.
    private  int  accountInSubtitleNumber()
    {
    	return getInSubtitleTotalByJni();
    }
    //wait to finish.
    private  void setInSubtitleNumber(int index)
    {
    	setInSubtitleNumberByJni(index);
    	return;
    }   
}


package com.subtitleparser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class SubtitleUtils {
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
        "sub",
        "idx",
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
			subfile= new File(filename);
			accountExSubtitleNumber();

		}
	}
	
    public int getSubTotal()
    {
    	return exSubtotle+accountInSubtitleNumber();
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
    	String prefix=tmp.substring(0, tmp.lastIndexOf('.'));
      	File DirFile= subfile.getParentFile();
    	if(DirFile.isDirectory())
    	{
	    	for (String file : DirFile.list()) 
	    	{
	    		if(file.startsWith(prefix))
	    		{
	    	        for (String ext : extensions) {
	    	            if (file.endsWith(ext))
	    	            {
	    	            	strlist.add(DirFile.getAbsolutePath()+"/"+file);
	    	            	break;
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
    	return 0;
    }
    //wait to finish.
    private  void setInSubtitleNumber(int index)
    {
    	return;
    }   
}


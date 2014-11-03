package com.subtitleparser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;
import android.os.SystemProperties;

public class SubtitleUtils {
	public native int getInSubtitleTotalByJni();
	
	//public native int setInSubtitleNumberByJni(int  ms);
	public native void setSubtitleNumberByJni(int  idx);
	public native int getCurrentInSubtitleIndexByJni();
//	public native void FileChangedByJni(String name);

	private String filename=null;
	private File subfile =null;
//	private List<String> strlist = new ArrayList<String>();
	private List<SubID> strlist = new ArrayList<SubID>();
	private int exSubtotle=0;
	private boolean supportLrc = true;//false; //lrc support
    
    public static final String[] extensions = {
    	"txt",
        "srt",
        "smi",
        "sami",
        "rt",
        "ssa",
        "ass",
        "lrc",
        "xml",
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
//			FileChangedByJni(name);
			subfile= new File(filename);

            if(SystemProperties.getBoolean("sys.extSubtitle.enable", true))
			    accountExSubtitleNumber();
		}
	}
	
	public int getExSubTotal()
    {
    	return exSubtotle;
    }
	
    public int getSubTotal()
    {
    	for(int i=0;i<exSubtotle;i++)
    	{
    		Log.v("Subfile list ",i+":"+getSubPath(i));
    	}
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
        
        if(index<getInSubTotal()) 
        {
            return "INSUB";
        }
        else if(index<(exSubtotle+accountInSubtitleNumber()))
        {
            return strlist.get(index - getInSubTotal()).filename;
        }
        return null;
    	
    	/*if(index<exSubtotle)
    		return strlist.get(index).filename;
    	else if(index<getSubTotal())
    	{
//    		setInSubtitleNumber(0xff);
//    		setInSubtitleNumber(index-exSubtotle);
    		return "INSUB";
    	}
		return null;*/

    }
    
    public SubID getSubID(int index)
    {
    	if(subfile==null)
    		return null ;

        if(index<getInSubTotal()) 
        {
            return new SubID("INSUB",index);
        }
        else if(index<getSubTotal())
        {
            return strlist.get(index - getInSubTotal());
        }
        return null;
    	/*
    	if(index<exSubtotle)
    		return strlist.get(index);
    	else if(index<getSubTotal())
    	{
//    		setInSubtitleNumber(0xff);
//    		setInSubtitleNumber(index-exSubtotle);
    		return new SubID("INSUB",index-exSubtotle);
    	}
		return null; */   
	}
    private void  accountExSubtitleNumber()
    {
    	String tmp=subfile.getName();
    	String prefix=tmp.substring(0, tmp.lastIndexOf('.')/*+1*/);
		Log.i("SubtitleUtils",	""+ prefix   );

      	File DirFile= subfile.getParentFile();
      	int idxindex=0;
       boolean skipLrc = false;
       int exSubIndex = getInSubTotal();
      	
    	if(DirFile.isDirectory())
    	{
            for (String file : DirFile.list()) 
            {
                if((file.toLowerCase()).startsWith(prefix.toLowerCase()))
                {
                    for (String ext : extensions) {
                        if (file.toLowerCase().endsWith(ext))
                        {
                            if(supportLrc == true)
                            {
                                skipLrc = false;
                            }
                            else
                            {
                                skipLrc = file.toLowerCase().endsWith("lrc"); //shield lrc file
                            }
                            
                            if(!skipLrc) 
                            {
                                strlist.add(new SubID(DirFile.getAbsolutePath()+"/"+file,exSubIndex));
                                exSubIndex++;
                            }
                            break;
                        }
                    }	    			
                }
            }
	    	for(SubID file : strlist)
	    	{
	    		if(file.filename.toLowerCase().endsWith("idx"))
	    		{
					strlist.remove(idxindex);
	    			Log.v("before: ",""+file );
	    			String st=file.filename.substring(0, file.filename.length()-3);
	    			for(int i=0;i<strlist.size();i++)
	    			{
	    				if(strlist.get(i).filename.toLowerCase().endsWith("sub")&&
	    						strlist.get(i).filename.startsWith(st)&&
	    						strlist.get(i).filename.length()==file.filename.length())
	    				{
	    	    			Log.v("accountExSubtitleNumber: ","clear "+strlist.get(i) );
	    					strlist.remove(i);
	    				}
	    			}
	    			accountIdxSubtitleNumber(file.filename);
	    	    	exSubtotle=strlist.size();
	    	    	break;
	    		}else
	    		{	
	    			idxindex++;
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
    public void setInSubtitleNumber(int index)
    {
    	//setInSubtitleNumberByJni(index);
    	return;
    }  
    public void setSubtitleNumber(int index)
    {
    	setSubtitleNumberByJni(index);
    	return;
    }   
    private int accountIdxSubtitleNumber( String filename )
    {
    	int idxcount =0;
    	String inputString=null;
		try {
			inputString = FileIO.file2string(filename, "GBK");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return idxcount;
		}
		String n="\\"+System.getProperty("line.separator");
		Pattern p = Pattern.compile("id:(.*?),\\s*"+"index:\\s*(\\d*)");
		Matcher m = p.matcher(inputString);
		while(m.find())
		{
			idxcount++;
			Log.v("accountIdxSubtitleNumber","id:"+m.group(1) +" index:"+m.group(2) );
			strlist.add(new SubID(filename,Integer.parseInt(m.group(2))));
		}
		return idxcount;
    }
}


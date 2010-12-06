/****************************************
 * file: sub_set_sys.c
 * description: set sys attr when
****************************************/
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <log_print.h>

int get_subtitle_enable()
{
    int fd;
	int subtitle_enable = 0;
    char *path = "/sys/class/subtitle/enable";    
	char  bcmd[16];
	fd=open(path, O_RDONLY);
	if(fd>=0)
	{    	
    	read(fd,bcmd,sizeof(bcmd));       
        subtitle_enable = strtol(bcmd, NULL, 16);       
        subtitle_enable &= 0x1;
    	close(fd);    	
	}
	return subtitle_enable;   
}

int get_subtitle_num()
{
    int fd;
	int subtitle_num = 0;
    char *path = "/sys/class/subtitle/total";    
	char  bcmd[16];
	fd=open(path, O_RDONLY);
	if(fd>=0)
	{    	
    	read(fd,bcmd,sizeof(bcmd)); 
		sscanf(bcmd, "%d", &subtitle_num);
    	close(fd);    	
	}
	return subtitle_num;   
}

int get_subtitle_curr()
{
    int fd;
	int subtitle_cur = 0;
    char *path = "/sys/class/subtitle/curr";    
	char  bcmd[16];
	fd=open(path, O_RDONLY);
	if(fd>=0)
	{    	
    	read(fd,bcmd,sizeof(bcmd)); 
		sscanf(bcmd, "%d", &subtitle_cur);
    	close(fd);    	
	}
	return subtitle_cur;   
}